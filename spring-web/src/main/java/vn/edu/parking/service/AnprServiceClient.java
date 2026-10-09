package vn.edu.parking.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Flow;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Service
public class AnprServiceClient {
    private static final int MAX_IMAGE_BYTES = 10 * 1024 * 1024;
    private static final int MAX_VIDEO_BYTES = 10 * 1024 * 1024;
    private static final int MAX_RESPONSE_BYTES = 16 * 1024 * 1024;
    private static final Set<String> IMAGE_EXTENSIONS = Set.of(".jpg", ".jpeg", ".png", ".bmp", ".webp");
    private static final Set<String> VIDEO_EXTENSIONS = Set.of(".mp4", ".avi", ".mov", ".mkv", ".wmv", ".m4v", ".webm");
    private static final Set<String> VEHICLE_TYPES = Set.of("CAR", "MOTORBIKE", "UNKNOWN");

    private final URI baseUri;
    private final String serviceToken;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;
    private final Duration requestTimeout;

    @org.springframework.beans.factory.annotation.Autowired
    public AnprServiceClient(
            @Value("${parking.anpr.base-url:http://localhost:8001}") String baseUrl,
            @Value("${parking.anpr.service-token:}") String serviceToken,
            ObjectMapper objectMapper) {
        this(baseUrl, serviceToken, objectMapper, Duration.ofSeconds(45));
    }

    AnprServiceClient(String baseUrl, String serviceToken, ObjectMapper objectMapper, Duration requestTimeout) {
        this.baseUri = validateBaseUri(baseUrl);
        this.serviceToken = serviceToken == null ? "" : serviceToken;
        this.objectMapper = objectMapper;
        this.requestTimeout = requestTimeout;
        this.httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(3))
            .followRedirects(HttpClient.Redirect.NEVER)
            .build();
    }

    public JsonNode recognizeImage(byte[] image, String extension) {
        return recognize(image, extension, IMAGE_EXTENSIONS, MAX_IMAGE_BYTES, "/recognize/image");
    }

    public JsonNode recognizeVideo(byte[] video, String extension) {
        return recognize(video, extension, VIDEO_EXTENSIONS, MAX_VIDEO_BYTES, "/recognize/video");
    }

    public JsonNode verifyFaceWithCamera(byte[] registrationImage) {
        if (registrationImage == null || registrationImage.length == 0)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Registration image is required");
        if (registrationImage.length > MAX_IMAGE_BYTES)
            throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE, "Registration image exceeds the allowed size");
        String boundary = "parking-" + UUID.randomUUID();
        byte[] body = faceCameraMultipartBody(boundary, registrationImage);
        JsonNode response = postInternal("/face/verify-camera", "multipart/form-data; boundary=" + boundary,
            HttpRequest.BodyPublishers.ofByteArray(body));
        return validateFaceResponse(response, false);
    }

    public JsonNode captureFaceWithCamera() {
        JsonNode response = postInternal("/face/capture-camera", "application/octet-stream",
            HttpRequest.BodyPublishers.noBody());
        return validateFaceResponse(response, true);
    }

    private JsonNode recognize(byte[] file, String extension, Set<String> allowedExtensions, int maxBytes,
            String endpoint) {
        if (file == null || file.length == 0)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Recognition file is required");
        if (file.length > maxBytes)
            throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE, "Recognition file exceeds the allowed size");
        if (extension == null || !allowedExtensions.contains(extension.toLowerCase(java.util.Locale.ROOT)))
            throw new ResponseStatusException(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "Unsupported recognition format");

        String normalizedExtension = extension.toLowerCase(java.util.Locale.ROOT);
        String boundary = "parking-" + UUID.randomUUID();
        byte[] body = multipartBody(boundary, normalizedExtension, file);
        return validateResponse(postInternal(endpoint, "multipart/form-data; boundary=" + boundary,
            HttpRequest.BodyPublishers.ofByteArray(body)));
    }

    private JsonNode postInternal(String endpoint, String contentType, HttpRequest.BodyPublisher body) {
        if (!validServiceToken(serviceToken))
            throw new AnprServiceUnavailableException();
        HttpRequest request = HttpRequest.newBuilder(baseUri.resolve(endpoint))
            .timeout(requestTimeout)
            .header("Authorization", "Bearer " + serviceToken)
            .header("Content-Type", contentType)
            .POST(body)
            .build();
        try {
            CompletableFuture<HttpResponse<BoundedResponseBody>> responseFuture = httpClient.sendAsync(request,
                responseInfo -> new BoundedResponseBodySubscriber(MAX_RESPONSE_BYTES));
            HttpResponse<BoundedResponseBody> response;
            try {
                response = responseFuture.get(requestTimeout.toMillis(), TimeUnit.MILLISECONDS);
            } catch (TimeoutException ex) {
                responseFuture.cancel(true);
                throw new AnprServiceUnavailableException(ex);
            } catch (ExecutionException ex) {
                if (ex.getCause() instanceof HttpTimeoutException)
                    throw new AnprServiceUnavailableException(ex.getCause());
                throw new AnprServiceUnavailableException(ex.getCause());
            }
            if (response.body().tooLarge())
                throw badGateway("ANPR response exceeded the allowed size");
            if (response.statusCode() >= 500)
                throw new AnprServiceUnavailableException();
            if (response.statusCode() != 200)
                throw badGateway("ANPR service rejected the recognition request");
            try {
                return objectMapper.readTree(response.body().bytes());
            } catch (IOException ex) {
                throw badGateway("ANPR service returned an invalid response");
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new AnprServiceUnavailableException(ex);
        } catch (AnprServiceUnavailableException ex) {
            throw ex;
        }
    }

    private static URI validateBaseUri(String value) {
        try {
            URI uri = URI.create(value.trim());
            String scheme = uri.getScheme();
            String host = uri.getHost();
            boolean loopback = host != null && Set.of("localhost", "127.0.0.1", "::1", "[::1]")
                .contains(host.toLowerCase(java.util.Locale.ROOT));
            if (host == null || uri.getUserInfo() != null || uri.getQuery() != null || uri.getFragment() != null
                    || !("https".equalsIgnoreCase(scheme) || ("http".equalsIgnoreCase(scheme) && loopback))
                    || (uri.getPath() != null && !uri.getPath().isEmpty() && !"/".equals(uri.getPath())))
                throw new IllegalArgumentException("ANPR base URL must use HTTPS or loopback HTTP without path, credentials, query or fragment");
            return uri;
        } catch (RuntimeException ex) {
            throw new IllegalArgumentException("Invalid ANPR base URL", ex);
        }
    }

    private JsonNode validateResponse(JsonNode response) {
        if (response == null || !response.isObject()
                || !textField(response, "plateText", 32)
                || !textField(response, "vehicleType", 16)
                || !VEHICLE_TYPES.contains(response.path("vehicleType").asText())
                || !confidence(response, "detectionConfidence")
                || !confidence(response, "ocrConfidence")
                || !confidence(response, "vehicleConfidence")
                || !response.path("frameIndex").canConvertToInt()
                || response.path("frameIndex").intValue() < 0
                || !textField(response, "annotatedImageBase64", 14 * 1024 * 1024)
                || !textField(response, "message", 512)
                || !validBoundingBox(response.path("boundingBox"))
                || !validAnnotatedImage(response.path("annotatedImageBase64").asText()))
            throw badGateway("ANPR service returned an invalid recognition response");
        return response;
    }

    private JsonNode validateFaceResponse(JsonNode response, boolean capture) {
        Set<String> decisions = capture ? Set.of("CAPTURED") : Set.of("PASS", "REVIEW", "REJECT");
        if (response == null || !response.isObject()
                || !textField(response, "decision", 16)
                || !decisions.contains(response.path("decision").asText())
                || !confidence(response, "similarity")
                || (!capture && !confidence(response, "matchThreshold"))
                || !textField(response, "message", 512)
                || !textField(response, "realtimeImageBase64", 14 * 1024 * 1024)
                || !validFaceImage(response.path("realtimeImageBase64").asText()))
            throw badGateway("ANPR service returned an invalid face response");
        return response;
    }

    private static boolean textField(JsonNode response, String name, int maxLength) {
        JsonNode value = response.get(name);
        return value != null && value.isTextual() && value.textValue().length() <= maxLength;
    }

    private static boolean confidence(JsonNode response, String name) {
        JsonNode value = response.get(name);
        if (value == null || !value.isNumber()) return false;
        double score = value.doubleValue();
        return Double.isFinite(score) && score >= 0.0 && score <= 1.0;
    }

    private static boolean validServiceToken(String token) {
        return token.length() >= 32 && token.chars().allMatch(character -> character >= 0x21 && character <= 0x7e);
    }

    private static boolean validBoundingBox(JsonNode box) {
        if (box == null || box.isNull()) return true;
        if (!box.isObject()) return false;
        for (String field : new String[]{"x", "y", "width", "height"}) {
            if (!box.path(field).canConvertToInt() || box.path(field).intValue() < 0) return false;
        }
        return box.path("width").intValue() > 0 && box.path("height").intValue() > 0;
    }

    private static boolean validAnnotatedImage(String encoded) {
        if (encoded.isEmpty()) return true;
        try {
            return Base64.getDecoder().decode(encoded).length <= MAX_IMAGE_BYTES;
        } catch (IllegalArgumentException ex) {
            return false;
        }
    }

    private static boolean validFaceImage(String encoded) {
        if (encoded.isEmpty()) return false;
        try {
            byte[] image = Base64.getDecoder().decode(encoded);
            return image.length > 0 && image.length <= MAX_IMAGE_BYTES;
        } catch (IllegalArgumentException ex) {
            return false;
        }
    }

    private static byte[] faceCameraMultipartBody(String boundary, byte[] registrationImage) {
        try {
            ByteArrayOutputStream output = new ByteArrayOutputStream(registrationImage.length + 512);
            output.write(("--" + boundary + "\r\n" +
                "Content-Disposition: form-data; name=\"registration\"; filename=\"registration.jpg\"\r\n" +
                "Content-Type: image/jpeg\r\n\r\n").getBytes(StandardCharsets.US_ASCII));
            output.write(registrationImage);
            output.write(("\r\n--" + boundary + "\r\n" +
                "Content-Disposition: form-data; name=\"cameraIndex\"\r\n\r\n0\r\n--" +
                boundary + "--\r\n").getBytes(StandardCharsets.US_ASCII));
            return output.toByteArray();
        } catch (IOException ex) {
            throw new IllegalStateException("Unable to build face verification request", ex);
        }
    }

    private static byte[] multipartBody(String boundary, String extension, byte[] image) {
        String contentType = switch (extension) {
            case ".jpg", ".jpeg" -> "image/jpeg";
            case ".png" -> "image/png";
            case ".bmp" -> "image/bmp";
            case ".webp" -> "image/webp";
            case ".mp4", ".m4v" -> "video/mp4";
            case ".mov" -> "video/quicktime";
            case ".avi" -> "video/x-msvideo";
            case ".mkv" -> "video/x-matroska";
            case ".wmv" -> "video/x-ms-wmv";
            case ".webm" -> "video/webm";
            default -> throw new ResponseStatusException(HttpStatus.UNSUPPORTED_MEDIA_TYPE);
        };
        try {
            ByteArrayOutputStream output = new ByteArrayOutputStream(image.length + 256);
            output.write(("--" + boundary + "\r\n" +
                "Content-Disposition: form-data; name=\"file\"; filename=\"upload" + extension + "\"\r\n" +
                "Content-Type: " + contentType + "\r\n\r\n").getBytes(StandardCharsets.US_ASCII));
            output.write(image);
            output.write(("\r\n--" + boundary + "--\r\n").getBytes(StandardCharsets.US_ASCII));
            return output.toByteArray();
        } catch (IOException ex) {
            throw new IllegalStateException("Unable to build ANPR request", ex);
        }
    }

    private static ResponseStatusException badGateway(String message) {
        return new ResponseStatusException(HttpStatus.BAD_GATEWAY, message);
    }

    private record BoundedResponseBody(byte[] bytes, boolean tooLarge) { }

    private static final class BoundedResponseBodySubscriber
            implements HttpResponse.BodySubscriber<BoundedResponseBody> {
        private final int maximumBytes;
        private final ByteArrayOutputStream body = new ByteArrayOutputStream();
        private final CompletableFuture<BoundedResponseBody> result = new CompletableFuture<>();
        private Flow.Subscription subscription;

        private BoundedResponseBodySubscriber(int maximumBytes) {
            this.maximumBytes = maximumBytes;
        }

        @Override
        public CompletionStage<BoundedResponseBody> getBody() {
            return result;
        }

        @Override
        public void onSubscribe(Flow.Subscription subscription) {
            this.subscription = subscription;
            subscription.request(Long.MAX_VALUE);
        }

        @Override
        public void onNext(List<ByteBuffer> buffers) {
            for (ByteBuffer buffer : buffers) {
                if (buffer.remaining() > maximumBytes - body.size()) {
                    subscription.cancel();
                    result.complete(new BoundedResponseBody(new byte[0], true));
                    return;
                }
                byte[] bytes = new byte[buffer.remaining()];
                buffer.get(bytes);
                body.write(bytes, 0, bytes.length);
            }
        }

        @Override
        public void onError(Throwable error) {
            result.completeExceptionally(error);
        }

        @Override
        public void onComplete() {
            result.complete(new BoundedResponseBody(body.toByteArray(), false));
        }
    }
}

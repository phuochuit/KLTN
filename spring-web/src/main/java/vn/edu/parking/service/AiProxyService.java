package vn.edu.parking.service;

import java.io.IOException;
import java.util.Locale;
import java.util.Set;
import java.net.URI;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.multipart.MultipartFile;

@Service
public class AiProxyService {
    private static final long MAX_IMAGE_BYTES = 10L * 1024 * 1024;
    private static final long MAX_VIDEO_BYTES = 25L * 1024 * 1024;
    private static final Set<String> IMAGE_TYPES = Set.of("image/jpeg", "image/png", "image/bmp", "image/webp");
    private static final Set<String> VIDEO_TYPES = Set.of("video/mp4", "video/x-msvideo", "video/quicktime",
        "video/x-matroska", "video/x-ms-wmv", "video/webm", "application/octet-stream");

    private final RestClient client;

    public AiProxyService(RestClient parkingAiRestClient) { this.client = parkingAiRestClient; }

    public byte[] health(String requestId) { return get("/health", requestId); }

    public byte[] recognizeImage(MultipartFile file, String requestId) {
        validate(file, IMAGE_TYPES, MAX_IMAGE_BYTES, "ảnh", requestId);
        return upload("/recognize/image", file, requestId);
    }

    public byte[] recognizeVideo(MultipartFile file, String requestId) {
        validate(file, VIDEO_TYPES, MAX_VIDEO_BYTES, "video", requestId);
        return upload("/recognize/video", file, requestId);
    }

    public byte[] recognizeCamera(int cameraIndex, int frameCount, String requestId) {
        return recognizeCamera(cameraIndex, null, frameCount, requestId);
    }

    public byte[] recognizeCamera(int cameraIndex, String cameraSource, int frameCount, String requestId) {
        if (cameraIndex < 0) {
            throw new AiProxyException(org.springframework.http.HttpStatus.BAD_REQUEST,
                "Chỉ số camera xe không hợp lệ.", requestId, null);
        }
        String source = validatedCameraSource(cameraSource, requestId);
        int boundedFrameCount = Math.max(4, Math.min(frameCount, 16));
        return exchange(() -> client.post().uri("/recognize/camera")
            .header("X-Request-ID", requestId)
            .contentType(MediaType.APPLICATION_JSON)
            .body(new RecognitionCameraRequest(cameraIndex, source, boundedFrameCount))
            .retrieve().body(byte[].class), requestId);
    }

    public byte[] verifyCamera(byte[] registration, int cameraIndex, String requestId) {
        return verifyCamera(registration, cameraIndex, null, requestId);
    }

    public byte[] verifyCamera(byte[] registration, int cameraIndex, String cameraSource, String requestId) {
        validateRegistration(registration, requestId);
        String source = validatedCameraSource(cameraSource, requestId);
        var multipart = registrationMultipart(registration);
        multipart.add("cameraIndex", Integer.toString(cameraIndex));
        if (source != null) multipart.add("cameraSource", source);
        return exchange(() -> client.post().uri("/face/verify-camera")
            .header("X-Request-ID", requestId)
            .contentType(MediaType.MULTIPART_FORM_DATA)
            .body(multipart)
            .retrieve().body(byte[].class), requestId);
    }

    public byte[] verifyFaceImage(byte[] registration, MultipartFile file, String requestId) {
        validateRegistration(registration, requestId);
        validate(file, IMAGE_TYPES, MAX_IMAGE_BYTES, "ảnh khuôn mặt", requestId);
        return uploadFace("/face/verify-image", registration, file, requestId);
    }

    public byte[] verifyFaceVideo(byte[] registration, MultipartFile file, String requestId) {
        validateRegistration(registration, requestId);
        validate(file, VIDEO_TYPES, MAX_VIDEO_BYTES, "video khuôn mặt", requestId);
        return uploadFace("/face/verify-video", registration, file, requestId);
    }

    public byte[] captureCamera(int cameraIndex, String requestId) {
        return captureCamera(cameraIndex, null, requestId);
    }

    public byte[] captureCamera(int cameraIndex, String cameraSource, String requestId) {
        String source = validatedCameraSource(cameraSource, requestId);
        return exchange(() -> client.post().uri("/face/capture-camera")
            .header("X-Request-ID", requestId).contentType(MediaType.APPLICATION_JSON)
            .body(new CameraCaptureRequest(cameraIndex, source)).retrieve().body(byte[].class), requestId);
    }

    private byte[] get(String path, String requestId) {
        return exchange(() -> client.get().uri(path).header("X-Request-ID", requestId)
            .retrieve().body(byte[].class), requestId);
    }

    private byte[] upload(String path, MultipartFile file, String requestId) {
        var multipart = new LinkedMultiValueMap<String, Object>();
        try {
            var resource = new ByteArrayResource(file.getBytes()) {
                @Override public String getFilename() { return safeFilename(file.getOriginalFilename()); }
            };
            var headers = new HttpHeaders();
            headers.setContentType(MediaType.parseMediaType(normalizedContentType(file)));
            multipart.add("file", new HttpEntity<>(resource, headers));
        } catch (IOException ex) {
            throw new AiProxyException(org.springframework.http.HttpStatus.BAD_REQUEST,
                "Không thể đọc tệp đã chọn.", requestId, ex);
        }
        return exchange(() -> client.post().uri(path).header("X-Request-ID", requestId)
            .contentType(MediaType.MULTIPART_FORM_DATA).body(multipart)
            .retrieve().body(byte[].class), requestId);
    }

    private byte[] uploadFace(String path, byte[] registration, MultipartFile file, String requestId) {
        var multipart = registrationMultipart(registration);
        try {
            var resource = new ByteArrayResource(file.getBytes()) {
                @Override public String getFilename() { return safeFilename(file.getOriginalFilename()); }
            };
            var headers = new HttpHeaders();
            headers.setContentType(MediaType.parseMediaType(normalizedContentType(file)));
            multipart.add("realtime", new HttpEntity<>(resource, headers));
        } catch (IOException ex) {
            throw new AiProxyException(org.springframework.http.HttpStatus.BAD_REQUEST,
                "Không thể đọc tệp khuôn mặt đã chọn.", requestId, ex);
        }
        return exchange(() -> client.post().uri(path).header("X-Request-ID", requestId)
            .contentType(MediaType.MULTIPART_FORM_DATA).body(multipart)
            .retrieve().body(byte[].class), requestId);
    }

    private byte[] exchange(RequestCall call, String requestId) {
        try {
            byte[] body = call.execute();
            return body == null ? new byte[0] : body;
        } catch (RestClientResponseException ex) {
            var status = ex.getStatusCode().is4xxClientError()
                ? org.springframework.http.HttpStatus.BAD_REQUEST
                : org.springframework.http.HttpStatus.BAD_GATEWAY;
            throw new AiProxyException(status, "Dịch vụ nhận dạng chưa thể xử lý yêu cầu.", requestId, ex);
        } catch (ResourceAccessException ex) {
            throw new AiProxyException(org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE,
                "Dịch vụ nhận dạng đang tạm thời không khả dụng.", requestId, ex);
        }
    }

    private static void validate(MultipartFile file, Set<String> allowedTypes, long maxBytes,
            String label, String requestId) {
        if (file == null || file.isEmpty())
            throw new AiProxyException(org.springframework.http.HttpStatus.BAD_REQUEST, "Vui lòng chọn " + label + ".", requestId, null);
        if (file.getSize() > maxBytes)
            throw new AiProxyException(org.springframework.http.HttpStatus.PAYLOAD_TOO_LARGE, "Tệp " + label + " vượt quá dung lượng cho phép.", requestId, null);
        if (!allowedTypes.contains(normalizedContentType(file)))
            throw new AiProxyException(org.springframework.http.HttpStatus.UNSUPPORTED_MEDIA_TYPE, "Định dạng " + label + " không được hỗ trợ.", requestId, null);
    }

    private static void validateRegistration(byte[] registration, String requestId) {
        if (registration == null || registration.length == 0 || registration.length > MAX_IMAGE_BYTES)
            throw new AiProxyException(org.springframework.http.HttpStatus.BAD_REQUEST,
                "Ảnh đăng ký không hợp lệ.", requestId, null);
    }

    private static LinkedMultiValueMap<String, Object> registrationMultipart(byte[] registration) {
        var multipart = new LinkedMultiValueMap<String, Object>();
        boolean png = registration.length >= 8 && registration[0] == (byte) 0x89
            && registration[1] == 0x50 && registration[2] == 0x4e && registration[3] == 0x47;
        boolean jpeg = registration.length >= 3 && registration[0] == (byte) 0xff
            && registration[1] == (byte) 0xd8 && registration[2] == (byte) 0xff;
        if (!png && !jpeg) throw new IllegalArgumentException("Ảnh đăng ký phải là JPEG hoặc PNG");
        var resource = new ByteArrayResource(registration) {
            @Override public String getFilename() { return png ? "registered-face.png" : "registered-face.jpg"; }
        };
        var headers = new HttpHeaders();
        headers.setContentType(png ? MediaType.IMAGE_PNG : MediaType.IMAGE_JPEG);
        multipart.add("registration", new HttpEntity<>(resource, headers));
        return multipart;
    }

    private static String normalizedContentType(MultipartFile file) {
        String value = file.getContentType();
        return value == null || value.isBlank() ? "application/octet-stream" : value.toLowerCase(Locale.ROOT);
    }

    private static String safeFilename(String filename) {
        String value = filename == null ? "upload.bin" : filename.replace('\\', '/');
        value = value.substring(value.lastIndexOf('/') + 1).replaceAll("[^A-Za-z0-9._-]", "_");
        return value.isBlank() ? "upload.bin" : value;
    }

    private static String validatedCameraSource(String source, String requestId) {
        if (source == null || source.isBlank()) return null;
        String value = source.trim();
        if (value.length() > 1024) throw invalidCameraSource(requestId);
        if (value.matches("[0-9]+")) return value;
        try {
            URI uri = URI.create(value);
            String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
            if (!Set.of("rtsp", "http", "https").contains(scheme) || uri.getHost() == null)
                throw invalidCameraSource(requestId);
            return value;
        } catch (IllegalArgumentException ex) {
            throw invalidCameraSource(requestId);
        }
    }

    private static AiProxyException invalidCameraSource(String requestId) {
        return new AiProxyException(org.springframework.http.HttpStatus.BAD_REQUEST,
            "Nguồn camera không hợp lệ.", requestId, null);
    }

    private record RecognitionCameraRequest(int cameraIndex, String cameraSource, int frameCount) { }
    private record CameraCaptureRequest(int cameraIndex, String cameraSource) { }
    @FunctionalInterface private interface RequestCall { byte[] execute(); }
}

package vn.edu.parking.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AnprServiceClientTest {
    private static final String SERVICE_TOKEN = "synthetic-service-token-for-anpr-tests-32";
    private static final String VALID_RESPONSE = """
        {"plateText":"59A112345","vehicleType":"MOTORBIKE","detectionConfidence":0.9,
         "ocrConfidence":0.8,"vehicleConfidence":0.7,"boundingBox":{"x":1,"y":2,"width":30,"height":10},
         "frameIndex":0,"annotatedImageBase64":"","message":"synthetic result"}
        """;

    private HttpServer server;
    private String baseUrl;

    @BeforeEach
    void startControlledService() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.start();
        baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
    }

    @AfterEach
    void stopControlledService() {
        server.stop(0);
    }

    @Test
    void forwardsImageOnlyToConfiguredInternalEndpointWithServiceBearer() throws Exception {
        AtomicReference<String> authorization = new AtomicReference<>();
        AtomicReference<String> contentType = new AtomicReference<>();
        AtomicReference<byte[]> body = new AtomicReference<>();
        server.createContext("/recognize/image", exchange -> {
            authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
            contentType.set(exchange.getRequestHeaders().getFirst("Content-Type"));
            body.set(exchange.getRequestBody().readAllBytes());
            byte[] response = VALID_RESPONSE.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });

        var result = new AnprServiceClient(baseUrl, SERVICE_TOKEN, new ObjectMapper())
            .recognizeImage(new byte[]{1, 2, 3}, ".jpg");

        assertEquals("Bearer " + SERVICE_TOKEN, authorization.get());
        assertTrue(contentType.get().startsWith("multipart/form-data; boundary="));
        assertTrue(new String(body.get(), StandardCharsets.ISO_8859_1).contains("filename=\"upload.jpg\""));
        assertTrue(containsBytes(body.get(), new byte[]{1, 2, 3}));
        assertEquals("59A112345", result.path("plateText").asText());
    }

    @Test
    void forwardsVideoOnlyToConfiguredInternalEndpointWithServiceBearer() throws Exception {
        AtomicReference<String> authorization = new AtomicReference<>();
        AtomicReference<byte[]> body = new AtomicReference<>();
        server.createContext("/recognize/video", exchange -> {
            authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
            body.set(exchange.getRequestBody().readAllBytes());
            byte[] response = VALID_RESPONSE.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });

        var result = new AnprServiceClient(baseUrl, SERVICE_TOKEN, new ObjectMapper())
            .recognizeVideo(new byte[]{1, 2, 3}, ".mp4");

        assertEquals("Bearer " + SERVICE_TOKEN, authorization.get());
        assertTrue(new String(body.get(), StandardCharsets.ISO_8859_1).contains("filename=\"upload.mp4\""));
        assertTrue(containsBytes(body.get(), new byte[]{1, 2, 3}));
        assertEquals("59A112345", result.path("plateText").asText());
    }

    @Test
    void forwardsRegistrationImageAndCameraIndexToProtectedFaceEndpoint() throws Exception {
        AtomicReference<String> authorization = new AtomicReference<>();
        AtomicReference<byte[]> body = new AtomicReference<>();
        server.createContext("/face/verify-camera", exchange -> {
            authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
            body.set(exchange.getRequestBody().readAllBytes());
            byte[] response = """
                {"decision":"PASS","similarity":0.8,"matchThreshold":0.363,
                 "message":"synthetic face result","realtimeImageBase64":"AQID"}
                """.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });

        var result = new AnprServiceClient(baseUrl, SERVICE_TOKEN, new ObjectMapper())
            .verifyFaceWithCamera(new byte[]{1, 2, 3});

        String multipart = new String(body.get(), StandardCharsets.ISO_8859_1);
        assertEquals("Bearer " + SERVICE_TOKEN, authorization.get());
        assertTrue(multipart.contains("name=\"registration\"; filename=\"registration.jpg\""));
        assertTrue(multipart.contains("name=\"cameraIndex\""));
        assertTrue(multipart.contains("\r\n0\r\n"));
        assertTrue(containsBytes(body.get(), new byte[]{1, 2, 3}));
        assertEquals("PASS", result.path("decision").asText());
    }

    @Test
    void capturesFaceOnlyThroughProtectedInternalEndpoint() throws Exception {
        AtomicReference<String> authorization = new AtomicReference<>();
        server.createContext("/face/capture-camera", exchange -> {
            authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
            byte[] response = """
                {"decision":"CAPTURED","similarity":0.0,
                 "message":"synthetic face capture","realtimeImageBase64":"AQID"}
                """.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });

        var result = new AnprServiceClient(baseUrl, SERVICE_TOKEN, new ObjectMapper()).captureFaceWithCamera();

        assertEquals("Bearer " + SERVICE_TOKEN, authorization.get());
        assertEquals("CAPTURED", result.path("decision").asText());
    }

    @Test
    void missingServiceCredentialFailsClosedBeforeMakingRequest() {
        var exception = assertThrows(AnprServiceUnavailableException.class,
            () -> new AnprServiceClient(baseUrl, "", new ObjectMapper())
                .recognizeImage(new byte[]{1}, ".jpg"));
        assertEquals("Recognition service is unavailable", exception.getMessage());
    }

    @Test
    void invalidServiceResponseIsRejected() {
        server.createContext("/recognize/image", exchange -> {
            byte[] response = "{\"plateText\":\"forged\"}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });

        var exception = assertThrows(ResponseStatusException.class,
            () -> new AnprServiceClient(baseUrl, SERVICE_TOKEN, new ObjectMapper())
                .recognizeImage(new byte[]{1}, ".jpg"));

        assertEquals(HttpStatus.BAD_GATEWAY, exception.getStatusCode());
    }

    @Test
    void slowInternalServiceIsMarkedUnavailable() {
        server.createContext("/recognize/image", exchange -> {
            try {
                Thread.sleep(500);
                exchange.sendResponseHeaders(200, 0);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
            } catch (java.io.IOException ignored) {
                // The timed-out test client has already closed the connection.
            } finally {
                exchange.close();
            }
        });

        var exception = assertThrows(AnprServiceUnavailableException.class,
            () -> new AnprServiceClient(baseUrl, SERVICE_TOKEN, new ObjectMapper(), Duration.ofMillis(50))
                .recognizeImage(new byte[]{1}, ".jpg"));

        assertTrue(exception.getCause() != null);
    }

    @Test
    void slowResponseBodyIsMarkedUnavailable() {
        server.createContext("/recognize/image", exchange -> {
            try {
                exchange.sendResponseHeaders(200, 0);
                Thread.sleep(500);
                exchange.getResponseBody().write(VALID_RESPONSE.getBytes(StandardCharsets.UTF_8));
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
            } catch (java.io.IOException ignored) {
                // The timed-out test client has already closed the connection.
            } finally {
                exchange.close();
            }
        });

        var exception = assertThrows(AnprServiceUnavailableException.class,
            () -> new AnprServiceClient(baseUrl, SERVICE_TOKEN, new ObjectMapper(), Duration.ofMillis(50))
                .recognizeImage(new byte[]{1}, ".jpg"));

        assertTrue(exception.getCause() != null);
    }

    private static boolean containsBytes(byte[] data, byte[] value) {
        outer: for (int start = 0; start <= data.length - value.length; start++) {
            for (int offset = 0; offset < value.length; offset++) {
                if (data[start + offset] != value[offset]) continue outer;
            }
            return true;
        }
        return false;
    }
}

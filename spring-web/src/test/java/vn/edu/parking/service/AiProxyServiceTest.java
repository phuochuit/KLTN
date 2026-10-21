package vn.edu.parking.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.client.RestClient;
import org.springframework.test.web.client.MockRestServiceServer;

import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class AiProxyServiceTest {
    private final AiProxyService service = new AiProxyService(mock(RestClient.class));

    @Test
    void rejectsEmptyImageAndKeepsRequestId() {
        var file = new MockMultipartFile("file", "plate.jpg", "image/jpeg", new byte[0]);

        assertThatThrownBy(() -> service.recognizeImage(file, "request-empty"))
            .isInstanceOfSatisfying(AiProxyException.class, ex -> {
                assertThat(ex.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
                assertThat(ex.getRequestId()).isEqualTo("request-empty");
            });
    }

    @Test
    void rejectsUnsupportedImageType() {
        var file = new MockMultipartFile("file", "payload.svg", "image/svg+xml", "data".getBytes());

        assertThatThrownBy(() -> service.recognizeImage(file, "request-type"))
            .isInstanceOfSatisfying(AiProxyException.class, ex -> {
                assertThat(ex.getStatus()).isEqualTo(HttpStatus.UNSUPPORTED_MEDIA_TYPE);
                assertThat(ex.getMessage()).doesNotContain("image/svg+xml");
            });
    }

    @Test
    void rejectsOversizedVideoBeforeCallingAiService() {
        var file = new MockMultipartFile("file", "large.mp4", "video/mp4", new byte[25 * 1024 * 1024 + 1]);

        assertThatThrownBy(() -> service.recognizeVideo(file, "request-large"))
            .isInstanceOfSatisfying(AiProxyException.class, ex ->
                assertThat(ex.getStatus()).isEqualTo(HttpStatus.PAYLOAD_TOO_LARGE));
    }

    @Test
    void rejectsUnsafeCameraSourcesWithoutEchoingSource() {
        for (String source : new String[] { "file:///private/video.mp4", "C:/camera.mp4", "ftp://camera/live" }) {
            assertThatThrownBy(() -> service.recognizeCamera(0, source, 8, "camera-invalid"))
                .isInstanceOfSatisfying(AiProxyException.class, ex -> {
                    assertThat(ex.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(ex.getRequestId()).isEqualTo("camera-invalid");
                    assertThat(ex.getMessage()).doesNotContain(source);
                });
        }
    }

    @Test
    void forwardsNetworkCameraSourceWithoutBreakingLegacyIndex() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://anpr.test");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        var proxy = new AiProxyService(builder.build());
        String source = "rtsp://camera-user:camera-password@192.168.1.20/live";
        server.expect(requestTo("http://anpr.test/recognize/camera"))
            .andExpect(header("X-Request-ID", "camera-network"))
            .andExpect(content().json("""
                {"cameraIndex":0,"cameraSource":"rtsp://camera-user:camera-password@192.168.1.20/live","frameCount":8}
                """))
            .andRespond(withSuccess("{}", org.springframework.http.MediaType.APPLICATION_JSON));

        assertThat(proxy.recognizeCamera(0, source, 8, "camera-network")).isEqualTo("{}".getBytes());
        server.verify();
    }

    @Test
    void keepsLegacyCameraIndexValidation() {
        assertThatThrownBy(() -> service.recognizeCamera(-1, 8, "camera-index"))
            .isInstanceOfSatisfying(AiProxyException.class, ex ->
                assertThat(ex.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST));
    }
}

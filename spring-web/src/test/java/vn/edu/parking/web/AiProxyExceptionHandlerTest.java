package vn.edu.parking.web;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class AiProxyExceptionHandlerTest {
    @Test
    void returnsBusinessValidationAsUnprocessableEntity() {
        var response = new AiProxyExceptionHandler().handleInvalidRequest(
            new IllegalArgumentException("Chủ xe chưa có ảnh khuôn mặt hợp lệ."));

        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("Chủ xe chưa có ảnh khuôn mặt hợp lệ.", response.getBody().get("message"));
    }
}

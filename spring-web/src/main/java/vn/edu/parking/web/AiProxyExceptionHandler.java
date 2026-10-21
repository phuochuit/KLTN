package vn.edu.parking.web;

import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import vn.edu.parking.service.AiProxyException;

@RestControllerAdvice(assignableTypes = AiProxyController.class)
public class AiProxyExceptionHandler {
    @ExceptionHandler(AiProxyException.class)
    ResponseEntity<Map<String, String>> handle(AiProxyException exception) {
        return ResponseEntity.status(exception.getStatus()).body(Map.of(
            "message", exception.getMessage(),
            "requestId", exception.getRequestId()));
    }
}

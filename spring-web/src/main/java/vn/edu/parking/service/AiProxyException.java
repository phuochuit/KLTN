package vn.edu.parking.service;

import org.springframework.http.HttpStatus;

public class AiProxyException extends RuntimeException {
    private final HttpStatus status;
    private final String requestId;

    public AiProxyException(HttpStatus status, String message, String requestId, Throwable cause) {
        super(message, cause);
        this.status = status;
        this.requestId = requestId;
    }

    public HttpStatus getStatus() { return status; }
    public String getRequestId() { return requestId; }
}

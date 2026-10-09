package vn.edu.parking.service;

public final class AnprServiceUnavailableException extends RuntimeException {
    public AnprServiceUnavailableException() {
        super("Recognition service is unavailable");
    }

    public AnprServiceUnavailableException(Throwable cause) {
        super("Recognition service is unavailable", cause);
    }
}

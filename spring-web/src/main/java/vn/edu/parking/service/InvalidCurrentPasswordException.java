package vn.edu.parking.service;

public final class InvalidCurrentPasswordException extends RuntimeException {
    public InvalidCurrentPasswordException() {
        super("Current password is incorrect");
    }
}

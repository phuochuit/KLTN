package vn.edu.parking.service;

public final class LastManagementAccountException extends RuntimeException {
    public LastManagementAccountException() {
        super("The final enabled MANAGEMENT account cannot be disabled or demoted");
    }
}

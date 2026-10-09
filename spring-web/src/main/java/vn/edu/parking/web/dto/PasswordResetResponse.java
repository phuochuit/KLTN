package vn.edu.parking.web.dto;

public record PasswordResetResponse(String temporaryPassword, boolean mustChangePassword) { }

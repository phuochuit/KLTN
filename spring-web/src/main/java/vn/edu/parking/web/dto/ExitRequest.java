package vn.edu.parking.web.dto;

import jakarta.validation.constraints.NotBlank;

public record ExitRequest(@NotBlank String plateNumber, String cardCode, String vehicleType,
    boolean manualOverride, Long familyMemberId, boolean faceVerified, Double faceSimilarity) {}

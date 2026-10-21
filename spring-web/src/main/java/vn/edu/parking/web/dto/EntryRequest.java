package vn.edu.parking.web.dto;

import jakarta.validation.constraints.NotBlank;

public record EntryRequest(String operationId, @NotBlank String plateNumber, String cardCode, String vehicleType,
    boolean manualOverride, Long familyMemberId, boolean faceVerified, Double faceSimilarity,
    String realtimeFaceImageBase64, String vehicleEvidenceId, String faceEvidenceId,
    String manualOverrideReasonCode, String manualOverrideReasonText) {

    public EntryRequest(String plateNumber, String cardCode, String vehicleType,
            boolean manualOverride, Long familyMemberId, boolean faceVerified, Double faceSimilarity,
            String realtimeFaceImageBase64, String vehicleEvidenceId, String faceEvidenceId,
            String manualOverrideReasonCode, String manualOverrideReasonText) {
        this(null, plateNumber, cardCode, vehicleType, manualOverride, familyMemberId, faceVerified,
            faceSimilarity, realtimeFaceImageBase64, vehicleEvidenceId, faceEvidenceId,
            manualOverrideReasonCode, manualOverrideReasonText);
    }
}

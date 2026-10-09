package vn.edu.parking.web.dto;

import jakarta.validation.constraints.NotBlank;

public record EntryRequest(@NotBlank String plateNumber, String cardCode, String vehicleType,
    boolean manualOverride, Long familyMemberId, boolean faceVerified, Double faceSimilarity,
    String realtimeFaceImageBase64, String evidenceId, String faceEvidenceId,
    @jakarta.validation.Valid ManualOverrideRequest override) {
    public EntryRequest(String plateNumber, String cardCode, String vehicleType, boolean manualOverride,
            Long familyMemberId, boolean faceVerified, Double faceSimilarity, String realtimeFaceImageBase64,
            String evidenceId, String faceEvidenceId) {
        this(plateNumber, cardCode, vehicleType, manualOverride, familyMemberId, faceVerified, faceSimilarity,
            realtimeFaceImageBase64, evidenceId, faceEvidenceId, null);
    }
}

package vn.edu.parking.web.dto;

import jakarta.validation.constraints.NotBlank;

public record ExitRequest(@NotBlank String plateNumber, String cardCode, String vehicleType,
    boolean manualOverride, Long familyMemberId, boolean faceVerified, Double faceSimilarity,
    String evidenceId, String faceEvidenceId,
    @jakarta.validation.Valid ManualOverrideRequest override) {
    public ExitRequest(String plateNumber, String cardCode, String vehicleType, boolean manualOverride,
            Long familyMemberId, boolean faceVerified, Double faceSimilarity, String evidenceId,
            String faceEvidenceId) {
        this(plateNumber, cardCode, vehicleType, manualOverride, familyMemberId, faceVerified, faceSimilarity,
            evidenceId, faceEvidenceId, null);
    }
}

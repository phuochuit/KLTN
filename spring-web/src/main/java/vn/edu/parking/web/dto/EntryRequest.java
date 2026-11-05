package vn.edu.parking.web.dto;

public record EntryRequest(String operationId, String plateNumber, String cardCode, String vehicleType,
    boolean manualOverride, Long familyMemberId, boolean faceVerified, Double faceSimilarity,
    String realtimeFaceImageBase64, String vehicleEvidenceId, String faceEvidenceId,
    String manualOverrideReasonCode, String manualOverrideReasonText, boolean noPlate) {

    public EntryRequest(String operationId, String plateNumber, String cardCode, String vehicleType,
            boolean manualOverride, Long familyMemberId, boolean faceVerified, Double faceSimilarity,
            String realtimeFaceImageBase64, String vehicleEvidenceId, String faceEvidenceId,
            String manualOverrideReasonCode, String manualOverrideReasonText) {
        this(operationId, plateNumber, cardCode, vehicleType, manualOverride, familyMemberId,
            faceVerified, faceSimilarity, realtimeFaceImageBase64, vehicleEvidenceId, faceEvidenceId,
            manualOverrideReasonCode, manualOverrideReasonText, false);
    }

    public EntryRequest(String plateNumber, String cardCode, String vehicleType,
            boolean manualOverride, Long familyMemberId, boolean faceVerified, Double faceSimilarity,
            String realtimeFaceImageBase64, String vehicleEvidenceId, String faceEvidenceId,
            String manualOverrideReasonCode, String manualOverrideReasonText) {
        this(null, plateNumber, cardCode, vehicleType, manualOverride, familyMemberId, faceVerified,
            faceSimilarity, realtimeFaceImageBase64, vehicleEvidenceId, faceEvidenceId,
            manualOverrideReasonCode, manualOverrideReasonText, false);
    }
}

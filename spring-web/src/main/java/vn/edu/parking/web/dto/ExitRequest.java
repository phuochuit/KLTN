package vn.edu.parking.web.dto;

import jakarta.validation.constraints.NotBlank;
import vn.edu.parking.domain.PaymentMethod;

public record ExitRequest(String operationId, @NotBlank String plateNumber, String cardCode, String vehicleType,
    boolean manualOverride, Long familyMemberId, boolean faceVerified, Double faceSimilarity,
    String vehicleEvidenceId, String faceEvidenceId,
    String manualOverrideReasonCode, String manualOverrideReasonText,
    PaymentMethod paymentMethod, String paymentReference) {

    public ExitRequest(String plateNumber, String cardCode, String vehicleType,
            boolean manualOverride, Long familyMemberId, boolean faceVerified, Double faceSimilarity,
            String vehicleEvidenceId, String faceEvidenceId,
            String manualOverrideReasonCode, String manualOverrideReasonText) {
        this(null, plateNumber, cardCode, vehicleType, manualOverride, familyMemberId, faceVerified,
            faceSimilarity, vehicleEvidenceId, faceEvidenceId, manualOverrideReasonCode,
            manualOverrideReasonText, null, null);
    }
}

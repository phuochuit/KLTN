package vn.edu.parking.web.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.NotBlank;
import vn.edu.parking.domain.GateOperationType;

public record FaceVerificationRequest(@NotNull GateOperationType operation, @NotBlank String plateNumber,
    Long familyMemberId, @NotBlank String evidenceId) {
    public FaceVerificationRequest(GateOperationType operation, String plateNumber, Long familyMemberId) {
        this(operation, plateNumber, familyMemberId, null);
    }
}

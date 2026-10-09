package vn.edu.parking.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import vn.edu.parking.domain.GateOperationType;

public record FaceCaptureRequest(@NotNull GateOperationType operation, @NotBlank String plateNumber) { }

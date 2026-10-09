package vn.edu.parking.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ManualOverrideRequest(@NotBlank @Size(min = 10, max = 450) String reason) { }

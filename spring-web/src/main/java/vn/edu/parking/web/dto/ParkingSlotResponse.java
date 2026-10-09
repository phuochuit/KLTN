package vn.edu.parking.web.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.LocalDateTime;

public record ParkingSlotResponse(
    Long id,
    String slotCode,
    String zoneName,
    String floor,
    String slotType,
    String allowedVehicleType,
    String statusOverride,
    String assignedPlate,
    @JsonInclude(JsonInclude.Include.NON_NULL)
    String assignedOwnerName,
    @JsonInclude(JsonInclude.Include.NON_NULL)
    String assignedOwnerPhone,
    @JsonInclude(JsonInclude.Include.NON_NULL)
    String assignedApartment,
    String occupiedPlate,
    LocalDateTime occupiedEntryTime,
    String borrowedPlate,
    LocalDateTime borrowedUntil,
    String borrowNotes,
    boolean overdue,
    String status,
    String statusDescription
) { }

package vn.edu.parking.web.dto;

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
    String assignedOwnerName,
    String assignedOwnerPhone,
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

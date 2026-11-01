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
    String statusDescription,
    Long floorId,
    Boolean isBasement,
    Long zoneId,
    String zonePrefix,
    Long vehicleTypeId,
    String vehicleTypeCode,
    String vehicleTypeName,
    Integer capacityUnits,
    String locationLabel
) {
    public ParkingSlotResponse(
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
    ) {
        this(
            id, slotCode, zoneName, floor, slotType, allowedVehicleType, statusOverride,
            assignedPlate, assignedOwnerName, assignedOwnerPhone, assignedApartment,
            occupiedPlate, occupiedEntryTime, borrowedPlate, borrowedUntil, borrowNotes,
            overdue, status, statusDescription,
            null, null, null, null, null, null, null, null, null
        );
    }
}

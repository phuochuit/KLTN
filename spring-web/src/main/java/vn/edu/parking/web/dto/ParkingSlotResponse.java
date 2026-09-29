package vn.edu.parking.web.dto;

public record ParkingSlotResponse(Long id, String slotCode, String zoneName,
    String assignedPlate, String occupiedPlate, String status) { }

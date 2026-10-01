package vn.edu.parking.web.dto;

public record BatchSlotGenerateRequest(
    String floor,
    String zoneName,
    String prefix,
    int startNumber,
    int count,
    String slotType,
    String vehicleType
) { }

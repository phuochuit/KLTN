package vn.edu.parking.web.dto;

import java.time.LocalDateTime;

public record OpenParkingSessionResponse(
    Long sessionId,
    String plateNumber,
    String vehicleType,
    LocalDateTime entryTime
) { }

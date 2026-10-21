package vn.edu.parking.web.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record ParkingResponse(
    Long sessionId,
    String plateNumber,
    String ownerName,
    String vehicleType,
    String cardCode,
    String status,
    LocalDateTime entryTime,
    LocalDateTime exitTime,
    BigDecimal fee,
    String message,
    boolean warning,
    String receiptNumber,
    String paymentMethod,
    String paymentReference
) {
    public ParkingResponse(Long sessionId, String plateNumber, String ownerName, String vehicleType,
            String cardCode, String status, LocalDateTime entryTime, LocalDateTime exitTime,
            BigDecimal fee, String message, boolean warning) {
        this(sessionId, plateNumber, ownerName, vehicleType, cardCode, status, entryTime, exitTime,
            fee, message, warning, null, null, null);
    }
}

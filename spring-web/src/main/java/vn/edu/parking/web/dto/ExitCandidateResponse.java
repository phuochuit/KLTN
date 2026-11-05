package vn.edu.parking.web.dto;

import java.time.LocalDateTime;

/** Lượt vào đang mở nhưng chưa có biển thật, dùng để nối khi xe ra. */
public record ExitCandidateResponse(Long sessionId, String plateNumber, LocalDateTime entryTime,
    String slotCode, String vehicleType) { }

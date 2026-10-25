package vn.edu.parking.web.dto;

import java.time.Instant;
import java.util.Map;

public record ExceptionReportItemResponse(
    String id,
    Instant timestamp,
    String category,
    String categoryLabel,
    String severity,
    String plateNumber,
    String operatorUsername,
    String operatorFullName,
    String shiftName,
    String operation,
    String reasonCode,
    String description,
    String statusOrOutcome,
    Long sessionId,
    Map<String, String> details
) {}

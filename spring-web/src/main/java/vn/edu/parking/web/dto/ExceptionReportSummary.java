package vn.edu.parking.web.dto;

public record ExceptionReportSummary(
    long totalExceptions,
    long manualOverridesCount,
    long securityAlertsCount,
    long paymentIssuesCount,
    long faceRejectsCount,
    long qualityWarningsCount
) {}

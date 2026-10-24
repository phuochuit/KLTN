package vn.edu.parking.web.dto;

import java.util.List;

public record ExceptionReportPageResponse(
    List<ExceptionReportItemResponse> items,
    ExceptionReportSummary summary,
    int totalItems
) {}

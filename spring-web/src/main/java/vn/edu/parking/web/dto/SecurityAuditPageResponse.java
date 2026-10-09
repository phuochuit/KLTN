package vn.edu.parking.web.dto;

import java.util.List;

public record SecurityAuditPageResponse(List<SecurityAuditEventResponse> events,
        int page, int size, long totalElements, int totalPages) { }

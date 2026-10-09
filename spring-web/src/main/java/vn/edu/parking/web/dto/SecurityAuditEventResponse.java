package vn.edu.parking.web.dto;

import java.time.Instant;

public record SecurityAuditEventResponse(Long id, Long actorAccountId, String actorUsername,
        String action, String targetType, String targetReference, String outcome,
        String reason, String evidenceReference, Instant occurredAt) { }

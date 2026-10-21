package vn.edu.parking.service;

import java.time.Clock;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.edu.parking.domain.SecurityAuditEvent;
import vn.edu.parking.repository.SecurityAuditRepository;

@Service
public class SecurityAuditRetentionService {
    private static final int MIN_RETENTION_DAYS = 30;
    private final SecurityAuditRepository events;
    private final Clock clock;
    private final int retentionDays;

    @Autowired
    public SecurityAuditRetentionService(SecurityAuditRepository events,
            @Value("${parking.security.audit.retention-days:365}") int retentionDays) {
        this(events, Clock.systemUTC(), retentionDays);
    }

    SecurityAuditRetentionService(SecurityAuditRepository events, Clock clock, int retentionDays) {
        if (retentionDays < MIN_RETENTION_DAYS)
            throw new IllegalArgumentException("Audit retention must be at least 30 days");
        this.events = events;
        this.clock = clock;
        this.retentionDays = retentionDays;
    }

    @Transactional
    public CleanupResult cleanup() {
        var cutoff = clock.instant().minus(Duration.ofDays(retentionDays));
        int deleted = 0;
        while (true) {
            var batch = events.findTop100ByOccurredAtBeforeOrderByOccurredAtAsc(cutoff);
            if (batch.isEmpty()) break;
            events.deleteAllInBatch(batch);
            deleted += batch.size();
            if (batch.size() < 100) break;
        }
        events.save(new SecurityAuditEvent(null, "system", "AUDIT_RETENTION_CLEANUP",
            "SECURITY_AUDIT", null, "SUCCESS", "DELETED_" + deleted, null, clock.instant()));
        return new CleanupResult(deleted, cutoff);
    }

    public record CleanupResult(int deletedEvents, java.time.Instant cutoff) { }
}

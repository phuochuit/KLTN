package vn.edu.parking.service;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Component
public class GateEvidenceCleanupJob {
    private final GateEvidenceRetentionService retention;

    public GateEvidenceCleanupJob(GateEvidenceRetentionService retention) {
        this.retention = retention;
    }

    @Scheduled(cron = "${parking.gate-evidence.cleanup-cron:0 0 2 * * *}",
        zone = "${parking.gate-evidence.cleanup-zone:UTC}")
    public void cleanupDaily() {
        retention.cleanup(Instant.now());
    }
}

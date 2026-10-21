package vn.edu.parking.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import vn.edu.parking.service.GateEvidenceRetentionService;

@Configuration
@EnableScheduling
@ConditionalOnProperty(name = "parking.evidence.cleanup-enabled", havingValue = "true")
public class GateEvidenceRetentionConfiguration {
    private final GateEvidenceRetentionService retention;
    public GateEvidenceRetentionConfiguration(GateEvidenceRetentionService retention) {
        this.retention = retention;
    }

    @Scheduled(cron = "${parking.evidence.cleanup-cron:0 0 3 * * *}", zone = "UTC")
    void cleanupGateEvidence() {
        retention.cleanup();
    }
}

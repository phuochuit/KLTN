package vn.edu.parking.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import vn.edu.parking.service.SecurityAuditRetentionService;

@Configuration
@EnableScheduling
@ConditionalOnProperty(name = "parking.security.audit.cleanup-enabled", havingValue = "true")
public class SecurityAuditRetentionConfiguration {
    private final SecurityAuditRetentionService retention;
    public SecurityAuditRetentionConfiguration(SecurityAuditRetentionService retention) {
        this.retention = retention;
    }

    @Scheduled(cron = "${parking.security.audit.cleanup-cron:0 30 2 * * *}", zone = "UTC")
    void cleanupExpiredAuditEvents() {
        retention.cleanup();
    }
}

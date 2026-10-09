package vn.edu.parking.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;

@Component
public class GateEvidencePolicy {
    private final int retentionDays;

    public GateEvidencePolicy(@Value("${parking.gate-evidence.retention-days:30}") int retentionDays) {
        if (retentionDays < 1) throw new IllegalArgumentException("Gate evidence retention must be positive");
        this.retentionDays = retentionDays;
    }

    public Instant expiresAt(Instant capturedAt) {
        if (capturedAt == null) throw new IllegalArgumentException("Capture time is required");
        return capturedAt.plus(Duration.ofDays(retentionDays));
    }

    public int retentionDays() { return retentionDays; }
}

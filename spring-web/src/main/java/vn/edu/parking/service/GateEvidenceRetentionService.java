package vn.edu.parking.service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.edu.parking.domain.*;
import vn.edu.parking.repository.*;

@Service
public class GateEvidenceRetentionService {
    private final GateEvidenceRepository evidences;
    private final GateEvidenceAssetRepository assets;
    private final PrivateEvidenceStorage storage;
    private final SecurityAuditRepository auditEvents;
    private final Clock clock;
    private final int retentionDays;

    @Autowired
    public GateEvidenceRetentionService(GateEvidenceRepository evidences,
            GateEvidenceAssetRepository assets, PrivateEvidenceStorage storage,
            SecurityAuditRepository auditEvents,
            @Value("${parking.evidence.retention-days:30}") int retentionDays) {
        this(evidences, assets, storage, auditEvents, Clock.systemUTC(), retentionDays);
    }

    GateEvidenceRetentionService(GateEvidenceRepository evidences,
            GateEvidenceAssetRepository assets, PrivateEvidenceStorage storage,
            SecurityAuditRepository auditEvents, Clock clock, int retentionDays) {
        if (retentionDays < 1)
            throw new IllegalArgumentException("Evidence retention must be at least one day");
        this.evidences = evidences;
        this.assets = assets;
        this.storage = storage;
        this.auditEvents = auditEvents;
        this.clock = clock;
        this.retentionDays = retentionDays;
    }

    @Transactional
    public CleanupResult cleanup() {
        Instant now = clock.instant();
        Instant cutoff = now.minus(Duration.ofDays(retentionDays));
        int deletedEvidence = 0, deletedAssets = 0, skippedOpen = 0, failures = 0;
        for (GateEvidence evidence : evidences
                .findTop100ByDeletedAtIsNullAndCreatedAtBeforeOrderByCreatedAtAsc(cutoff)) {
            if (evidence.getParkingSession() != null
                    && evidence.getParkingSession().getStatus() == SessionStatus.OPEN) {
                skippedOpen++;
                continue;
            }
            if (evidence.getStatus() == EvidenceStatus.READY) evidence.expire(now);
            boolean complete = true;
            for (GateEvidenceAsset asset : assets.findByEvidenceIdAndDeletedAtIsNull(evidence.getId())) {
                try {
                    storage.delete(asset.getStorageKey());
                    asset.markDeleted(now);
                    assets.save(asset);
                    deletedAssets++;
                } catch (RuntimeException ex) {
                    failures++;
                    complete = false;
                }
            }
            if (complete) {
                evidence.markDeleted(now);
                evidences.save(evidence);
                deletedEvidence++;
            }
        }
        String reason = "EVIDENCE_" + deletedEvidence + "_ASSETS_" + deletedAssets
            + "_SKIPPED_OPEN_" + skippedOpen + "_FAILURES_" + failures;
        auditEvents.save(new SecurityAuditEvent(null, "system", "GATE_EVIDENCE_RETENTION_CLEANUP",
            "GATE_EVIDENCE", null, failures == 0 ? "SUCCESS" : "FAILURE", reason, null, now));
        return new CleanupResult(deletedEvidence, deletedAssets, skippedOpen, failures, cutoff);
    }

    public record CleanupResult(int deletedEvidence, int deletedAssets,
        int skippedOpenSessions, int failures, Instant cutoff) { }
}

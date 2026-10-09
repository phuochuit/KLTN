package vn.edu.parking.service;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.edu.parking.domain.GateEvidence;
import vn.edu.parking.repository.GateEvidenceRepository;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

@Service
public class GateEvidenceRetentionService {
    private static final int BATCH_SIZE = 100;
    private static final Duration ORPHAN_GRACE_PERIOD = Duration.ofHours(24);
    private final GateEvidenceRepository evidence;
    private final GateEvidenceStorage storage;
    private final SecurityAuditService audit;

    public GateEvidenceRetentionService(GateEvidenceRepository evidence, GateEvidenceStorage storage,
            SecurityAuditService audit) {
        this.evidence = evidence;
        this.storage = storage;
        this.audit = audit;
    }

    @Transactional
    public CleanupResult cleanup(Instant now) {
        if (now == null) throw new IllegalArgumentException("Cleanup time is required");
        int expiredDeleted = 0;
        int failed = 0;
        while (true) {
            List<GateEvidence> batch = evidence.findEligibleForDeletion(now, PageRequest.of(0, BATCH_SIZE));
            if (batch.isEmpty()) break;
            int deletedInBatch = 0;
            for (GateEvidence item : batch) {
                try {
                    storage.delete(item.getFileName());
                    if (item.getDerivedFileName() != null) storage.delete(item.getDerivedFileName());
                    evidence.delete(item);
                    expiredDeleted++;
                    deletedInBatch++;
                } catch (IOException | IllegalArgumentException ex) {
                    failed++;
                }
            }
            if (deletedInBatch == 0) break;
        }

        int orphanDeleted = 0;
        try {
            orphanDeleted = storage.deleteOrphans(now.minus(ORPHAN_GRACE_PERIOD), evidence::isFileReferenced);
        } catch (IOException ex) {
            failed++;
        }

        String outcome = failed == 0 ? "SUCCESS" : "PARTIAL";
        String summary = "expiredDeleted=" + expiredDeleted + ";orphanDeleted=" + orphanDeleted + ";failed=" + failed;
        audit.record(null, "GATE_EVIDENCE_RETENTION_CLEANUP", "GATE_EVIDENCE", "daily", outcome, summary, null);
        return new CleanupResult(expiredDeleted, orphanDeleted, failed);
    }

    public record CleanupResult(int expiredDeleted, int orphanDeleted, int failed) { }
}

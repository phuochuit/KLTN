package vn.edu.parking.service;

import org.junit.jupiter.api.Test;
import vn.edu.parking.domain.GateEvidence;
import vn.edu.parking.domain.GateEvidenceKind;
import vn.edu.parking.domain.GateOperationType;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class GateEvidencePolicyTest {
    @Test
    void defaultsToThirtyDaysFromCaptureAndRejectsNonpositiveRetention() {
        Instant capturedAt = Instant.parse("2026-01-01T00:00:00Z");

        GateEvidencePolicy policy = new GateEvidencePolicy(30);

        assertEquals(capturedAt.plusSeconds(30L * 24 * 60 * 60), policy.expiresAt(capturedAt));
        assertThrows(IllegalArgumentException.class, () -> new GateEvidencePolicy(0));
        assertThrows(IllegalArgumentException.class, () -> new GateEvidencePolicy(-1));
    }

    @Test
    void preservationRequiresReasonAndFiniteFutureDeadlineAndCanBeReleased() {
        Instant now = Instant.parse("2026-01-01T00:00:00Z");
        GateEvidence item = new GateEvidence("evidence-id", 7L, "session-id", GateOperationType.ENTRY,
            GateEvidenceKind.FACE_VERIFICATION, "file.bin", "image/jpeg", 1, "a".repeat(64),
            now, now.plusSeconds(30L * 24 * 60 * 60), now);

        assertThrows(IllegalArgumentException.class,
            () -> item.approvePreservation(8L, null, "investigation", now));
        assertThrows(IllegalArgumentException.class,
            () -> item.approvePreservation(8L, now, "investigation", now));
        assertThrows(IllegalArgumentException.class,
            () -> item.approvePreservation(8L, now.plusSeconds(60), " ", now));

        item.approvePreservation(8L, now.plusSeconds(3600), "Incident review", now);
        assertEquals(8L, item.getPreserveApprovedBy());
        assertEquals("Incident review", item.getPreserveReason());
        assertEquals(now.plusSeconds(3600), item.getPreserveUntil());

        item.releasePreservation();
        assertNull(item.getPreserveUntil());
        assertNull(item.getPreserveReason());
        assertNull(item.getPreserveApprovedBy());
    }
}

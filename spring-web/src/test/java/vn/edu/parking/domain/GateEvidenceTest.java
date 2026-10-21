package vn.edu.parking.domain;

import static org.junit.jupiter.api.Assertions.*;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class GateEvidenceTest {
    @Test
    void consumesReadyEvidenceOnlyOnce() {
        Instant now = Instant.parse("2026-10-17T03:00:00Z");
        var account = new SystemAccount("gate", "hash", AccountRole.GATE_STAFF, false);
        var desktop = new DesktopSession(UUID.randomUUID().toString(), account, now, now.plusSeconds(3600), "test");
        var evidence = evidence(account, desktop, now, now.plusSeconds(600));
        var parking = new ParkingSession();

        evidence.markReady();
        evidence.consume(parking, account, now.plusSeconds(1));

        assertEquals(EvidenceStatus.CONSUMED, evidence.getStatus());
        assertSame(parking, evidence.getParkingSession());
        assertThrows(IllegalStateException.class,
            () -> evidence.consume(parking, account, now.plusSeconds(2)));
    }

    @Test
    void rejectsExpiredEvidence() {
        Instant now = Instant.parse("2026-10-17T03:00:00Z");
        var account = new SystemAccount("gate", "hash", AccountRole.GATE_STAFF, false);
        var desktop = new DesktopSession(UUID.randomUUID().toString(), account, now, now.plusSeconds(3600), "test");
        var evidence = evidence(account, desktop, now.minusSeconds(120), now.minusSeconds(1));
        evidence.markReady();

        assertThrows(IllegalStateException.class,
            () -> evidence.consume(new ParkingSession(), account, now));
    }

    @Test
    void onlyCreatedEvidenceCanBecomeReady() {
        Instant now = Instant.parse("2026-10-17T03:00:00Z");
        var account = new SystemAccount("gate", "hash", AccountRole.GATE_STAFF, false);
        var desktop = new DesktopSession(UUID.randomUUID().toString(), account, now, now.plusSeconds(3600), "test");
        var evidence = evidence(account, desktop, now, now.plusSeconds(600));
        evidence.markReady();
        assertThrows(IllegalStateException.class, evidence::markReady);
    }

    private static GateEvidence evidence(SystemAccount account, DesktopSession desktop,
            Instant created, Instant expires) {
        return new GateEvidence(UUID.randomUUID().toString(), EvidenceType.VEHICLE,
            EvidenceOperation.ENTRY, EvidenceSourceKind.IMAGE, account, desktop,
            UUID.randomUUID().toString(), "test-model", "a".repeat(64), created, expires);
    }
}

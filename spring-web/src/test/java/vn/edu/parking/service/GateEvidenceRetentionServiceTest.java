package vn.edu.parking.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;
import vn.edu.parking.domain.*;
import vn.edu.parking.repository.*;

class GateEvidenceRetentionServiceTest {
    private static final Instant NOW = Instant.parse("2026-10-19T00:00:00Z");

    @Test
    void deletesPrivateAssetsAndMarksOldEvidence() {
        GateEvidenceRepository evidences = mock(GateEvidenceRepository.class);
        GateEvidenceAssetRepository assets = mock(GateEvidenceAssetRepository.class);
        PrivateEvidenceStorage storage = mock(PrivateEvidenceStorage.class);
        SecurityAuditRepository audit = mock(SecurityAuditRepository.class);
        GateEvidence evidence = mock(GateEvidence.class);
        GateEvidenceAsset asset = mock(GateEvidenceAsset.class);
        when(evidence.getId()).thenReturn("evidence-1");
        when(evidence.getStatus()).thenReturn(EvidenceStatus.CONSUMED);
        when(asset.getStorageKey()).thenReturn(
            "evidence/10000000-0000-0000-0000-000000000001/REALTIME_FACE/20000000-0000-0000-0000-000000000002.jpg");
        when(evidences.findTop100ByDeletedAtIsNullAndCreatedAtBeforeOrderByCreatedAtAsc(any()))
            .thenReturn(List.of(evidence));
        when(assets.findByEvidenceIdAndDeletedAtIsNull("evidence-1")).thenReturn(List.of(asset));
        var service = new GateEvidenceRetentionService(evidences, assets, storage, audit,
            Clock.fixed(NOW, ZoneOffset.UTC), 30);

        var result = service.cleanup();

        assertEquals(1, result.deletedEvidence());
        assertEquals(1, result.deletedAssets());
        verify(storage).delete(asset.getStorageKey());
        verify(asset).markDeleted(NOW);
        verify(evidence).markDeleted(NOW);
        verify(audit).save(any(SecurityAuditEvent.class));
    }

    @Test
    void preservesEvidenceForOpenParkingSession() {
        GateEvidenceRepository evidences = mock(GateEvidenceRepository.class);
        GateEvidenceAssetRepository assets = mock(GateEvidenceAssetRepository.class);
        GateEvidence evidence = mock(GateEvidence.class);
        ParkingSession parking = new ParkingSession();
        parking.setStatus(SessionStatus.OPEN);
        when(evidence.getParkingSession()).thenReturn(parking);
        when(evidences.findTop100ByDeletedAtIsNullAndCreatedAtBeforeOrderByCreatedAtAsc(any()))
            .thenReturn(List.of(evidence));
        var service = new GateEvidenceRetentionService(evidences, assets,
            mock(PrivateEvidenceStorage.class), mock(SecurityAuditRepository.class),
            Clock.fixed(NOW, ZoneOffset.UTC), 30);

        var result = service.cleanup();

        assertEquals(1, result.skippedOpenSessions());
        verifyNoInteractions(assets);
        verify(evidence, never()).markDeleted(any());
    }

    @Test
    void rejectsNonPositiveRetention() {
        assertThrows(IllegalArgumentException.class, () -> new GateEvidenceRetentionService(
            mock(GateEvidenceRepository.class), mock(GateEvidenceAssetRepository.class),
            mock(PrivateEvidenceStorage.class), mock(SecurityAuditRepository.class),
            Clock.fixed(NOW, ZoneOffset.UTC), 0));
    }
}

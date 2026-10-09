package vn.edu.parking.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;
import vn.edu.parking.domain.GateEvidence;
import vn.edu.parking.domain.GateEvidenceKind;
import vn.edu.parking.domain.GateOperationType;
import vn.edu.parking.repository.GateEvidenceRepository;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GateEvidenceRetentionServiceTest {
    @TempDir
    Path temporaryDirectory;

    @Mock GateEvidenceRepository evidence;
    @Mock SecurityAuditService audit;

    @Test
    void removesExpiredOriginalAndDerivedFilesAndAuditsOnlyCounts() throws Exception {
        Instant now = Instant.parse("2026-02-01T00:00:00Z");
        GateEvidenceStorage storage = storage();
        String original = storage.store(new byte[]{1}, 10);
        String derived = storage.store(new byte[]{2}, 10);
        GateEvidence expired = evidence(now.minus(31, ChronoUnit.DAYS), original);
        expired.setRecognition("60A12345", "MOTORBIKE", 0.8, 0.9, 0.7,
            derived, 1L, "b".repeat(64), null, "anpr-image");
        when(evidence.findEligibleForDeletion(eq(now), any(Pageable.class)))
            .thenReturn(List.of(expired), List.of());

        GateEvidenceRetentionService.CleanupResult result =
            new GateEvidenceRetentionService(evidence, storage, audit).cleanup(now);

        assertEquals(new GateEvidenceRetentionService.CleanupResult(1, 0, 0), result);
        assertFalse(Files.exists(storage.rootPath().resolve(original)));
        assertFalse(Files.exists(storage.rootPath().resolve(derived)));
        verify(evidence).delete(expired);
        ArgumentCaptor<String> summary = ArgumentCaptor.forClass(String.class);
        verify(audit).record(eq(null), eq("GATE_EVIDENCE_RETENTION_CLEANUP"), eq("GATE_EVIDENCE"),
            eq("daily"), eq("SUCCESS"), summary.capture(), eq(null));
        assertEquals("expiredDeleted=1;orphanDeleted=0;failed=0", summary.getValue());
    }

    @Test
    void preservesReferencedAndYoungFilesWhileDeletingOldOrphanFiles() throws Exception {
        Instant now = Instant.parse("2026-02-01T00:00:00Z");
        GateEvidenceStorage storage = storage();
        String referenced = storage.store(new byte[]{1}, 10);
        String orphan = storage.store(new byte[]{2}, 10);
        Files.setLastModifiedTime(storage.rootPath().resolve(referenced),
            FileTime.from(now.minus(2, ChronoUnit.DAYS)));
        Files.setLastModifiedTime(storage.rootPath().resolve(orphan),
            FileTime.from(now.minus(2, ChronoUnit.DAYS)));
        when(evidence.findEligibleForDeletion(eq(now), any(Pageable.class))).thenReturn(List.of());
        when(evidence.isFileReferenced(any())).thenAnswer(invocation ->
            referenced.equals(invocation.getArgument(0)));

        GateEvidenceRetentionService.CleanupResult result =
            new GateEvidenceRetentionService(evidence, storage, audit).cleanup(now);

        assertEquals(new GateEvidenceRetentionService.CleanupResult(0, 1, 0), result);
        assertTrue(Files.exists(storage.rootPath().resolve(referenced)));
        assertFalse(Files.exists(storage.rootPath().resolve(orphan)));
        verify(evidence, never()).delete(any(GateEvidence.class));
    }

    private GateEvidence evidence(Instant capturedAt, String filename) {
        return new GateEvidence("evidence-id", 7L, "desktop-session-id", GateOperationType.ENTRY,
            GateEvidenceKind.AI_RECOGNITION, filename, "image/jpeg", 1, "a".repeat(64), capturedAt,
            capturedAt.plus(30, ChronoUnit.DAYS), capturedAt);
    }

    private GateEvidenceStorage storage() throws Exception {
        return new GateEvidenceStorage(temporaryDirectory.resolve("gate-evidence").toString(),
            temporaryDirectory.resolve("private-media").toString());
    }
}

package vn.edu.parking.domain;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class GateEvidenceOverrideStateTest {
    @Test
    void onlyPendingRecognitionCanBeMarkedUnavailable() {
        Instant now = Instant.now();
        GateEvidence evidence = new GateEvidence("evidence-id", 1L, "desktop-session-id",
            GateOperationType.ENTRY, GateEvidenceKind.AI_RECOGNITION, "evidence.bin", "image/jpeg",
            1, "a".repeat(64), now, now.plusSeconds(3600), now);

        evidence.markRecognitionUnavailable(now);

        assertEquals("UNAVAILABLE", evidence.getRecognitionStatus());
        assertEquals(now, evidence.getRecognitionFailureAt());
        assertThrows(IllegalStateException.class, () -> evidence.markRecognitionUnavailable(now));
    }

    @Test
    void recognitionResultAndCameraFailureHaveExplicitServerStates() {
        Instant now = Instant.now();
        GateEvidence evidence = new GateEvidence("evidence-id", 1L, "desktop-session-id",
            GateOperationType.EXIT, GateEvidenceKind.AI_RECOGNITION, "evidence.bin", "image/jpeg",
            1, "a".repeat(64), now, now.plusSeconds(3600), now);

        evidence.setRecognition("77A12345", "MOTORBIKE", 0.9, 0.8, 0.7,
            null, null, null, 0, "fastapi-image");
        evidence.markFaceVerificationUnavailable(now);

        assertEquals("SUCCESS", evidence.getRecognitionStatus());
        assertEquals("UNAVAILABLE", evidence.getFaceVerificationStatus());
        assertEquals(now, evidence.getFaceVerificationFailureAt());
    }
}

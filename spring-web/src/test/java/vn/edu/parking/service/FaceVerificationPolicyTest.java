package vn.edu.parking.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class FaceVerificationPolicyTest {
    @Test
    void derivesPassReviewAndRejectFromBackendThresholds() {
        FaceVerificationPolicy policy = new FaceVerificationPolicy(0.30, 0.363);

        assertEquals("REJECT", policy.decision(0.2999));
        assertEquals("REVIEW", policy.decision(0.30));
        assertEquals("REVIEW", policy.decision(0.3629));
        assertEquals("PASS", policy.decision(0.363));
    }

    @Test
    void rejectsInvalidScoresAndThresholdConfigurations() {
        FaceVerificationPolicy policy = new FaceVerificationPolicy(0.30, 0.363);

        assertThrows(IllegalArgumentException.class, () -> policy.decision(Double.NaN));
        assertThrows(IllegalArgumentException.class, () -> policy.decision(1.01));
        assertThrows(IllegalArgumentException.class, () -> new FaceVerificationPolicy(0.4, 0.3));
        assertThrows(IllegalArgumentException.class, () -> new FaceVerificationPolicy(-0.1, 0.3));
    }
}

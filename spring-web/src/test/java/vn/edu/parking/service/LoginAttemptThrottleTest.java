package vn.edu.parking.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LoginAttemptThrottleTest {
    @Test
    void blocksOnlyTheUsernameAndAddressPairAfterTenFailures() {
        var throttle = new LoginAttemptThrottle();
        for (int i = 0; i < LoginAttemptThrottle.MAX_FAILED_ATTEMPTS; i++)
            throttle.recordFailure("manager", "127.0.0.1");

        assertTrue(throttle.isBlocked("manager", "127.0.0.1"));
        assertFalse(throttle.isBlocked("other-user", "127.0.0.1"));
        assertFalse(throttle.isBlocked("manager", "127.0.0.2"));
    }

    @Test
    void successfulLoginClearsFailureWindow() {
        var throttle = new LoginAttemptThrottle();
        for (int i = 0; i < LoginAttemptThrottle.MAX_FAILED_ATTEMPTS; i++)
            throttle.recordFailure(" Manager ", "127.0.0.1");
        throttle.recordSuccess("manager", "127.0.0.1");
        assertFalse(throttle.isBlocked("MANAGER", "127.0.0.1"));
    }
}

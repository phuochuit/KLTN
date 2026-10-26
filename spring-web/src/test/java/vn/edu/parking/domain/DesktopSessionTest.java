package vn.edu.parking.domain;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import org.junit.jupiter.api.Test;

class DesktopSessionTest {
    private final Instant createdAt = Instant.parse("2026-10-25T07:00:00Z");

    @Test
    void recentHeartbeatIsOnline() {
        var session = session();

        assertTrue(session.isOnlineAt(createdAt.plusSeconds(5)));
    }

    @Test
    void missingHeartbeatIsOfflineButAuthenticationSessionRemainsActive() {
        var session = session();
        Instant afterPresenceTimeout = createdAt.plusSeconds(6);

        assertFalse(session.isOnlineAt(afterPresenceTimeout));
        assertTrue(session.isActiveAt(afterPresenceTimeout));
    }

    @Test
    void revokedSessionIsImmediatelyOffline() {
        var session = session();
        session.revoke(createdAt.plusSeconds(1));

        assertFalse(session.isOnlineAt(createdAt.plusSeconds(2)));
    }

    private DesktopSession session() {
        var account = new SystemAccount("gate", "{noop}secret", AccountRole.GATE_STAFF, true);
        return new DesktopSession("session-1", account, createdAt,
            createdAt.plusSeconds(7200), "Parking desktop");
    }
}

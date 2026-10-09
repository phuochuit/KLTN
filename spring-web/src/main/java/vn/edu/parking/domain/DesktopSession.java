package vn.edu.parking.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.Duration;
import java.time.Instant;

@Entity
@Table(name = "desktop_sessions")
public class DesktopSession {
    private static final Duration IDLE_TIMEOUT = Duration.ofHours(2);

    @Id
    @Column(name = "session_id", length = 36, nullable = false)
    private String sessionId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "account_id", nullable = false)
    private SystemAccount account;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "last_seen_at", nullable = false)
    private Instant lastSeenAt;

    @Column(name = "absolute_expires_at", nullable = false)
    private Instant absoluteExpiresAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    @Column(name = "security_version", nullable = false)
    private long securityVersion;

    @Column(name = "device_label", length = 120)
    private String deviceLabel;

    protected DesktopSession() { }

    public DesktopSession(String sessionId, SystemAccount account, Instant createdAt,
            Instant absoluteExpiresAt, String deviceLabel) {
        this.sessionId = sessionId;
        this.account = account;
        this.createdAt = createdAt;
        this.lastSeenAt = createdAt;
        this.absoluteExpiresAt = absoluteExpiresAt;
        this.securityVersion = account.getSecurityVersion();
        this.deviceLabel = deviceLabel;
    }

    public boolean isActiveAt(Instant now) {
        return revokedAt == null && now.isBefore(absoluteExpiresAt)
            && now.isBefore(lastSeenAt.plus(IDLE_TIMEOUT));
    }

    public void touch(Instant now) {
        if (now.isAfter(lastSeenAt)) lastSeenAt = now;
    }

    public void revoke(Instant now) {
        if (revokedAt == null) revokedAt = now;
    }

    public String getSessionId() { return sessionId; }
    public SystemAccount getAccount() { return account; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getLastSeenAt() { return lastSeenAt; }
    public Instant getAbsoluteExpiresAt() { return absoluteExpiresAt; }
    public Instant getRevokedAt() { return revokedAt; }
    public long getSecurityVersion() { return securityVersion; }
    public String getDeviceLabel() { return deviceLabel; }
}

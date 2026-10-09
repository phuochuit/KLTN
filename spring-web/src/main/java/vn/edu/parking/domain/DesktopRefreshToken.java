package vn.edu.parking.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.Instant;

@Entity
@Table(name = "desktop_refresh_tokens", uniqueConstraints =
    @UniqueConstraint(name = "uk_desktop_refresh_tokens_hash", columnNames = "token_hash"))
public class DesktopRefreshToken {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "session_id", nullable = false)
    private DesktopSession session;

    @Column(name = "token_hash", length = 64, nullable = false, unique = true)
    private String tokenHash;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "consumed_at")
    private Instant consumedAt;

    @Column(name = "replaced_by_hash", length = 64)
    private String replacedByHash;

    protected DesktopRefreshToken() { }

    public DesktopRefreshToken(DesktopSession session, String tokenHash, Instant createdAt,
            Instant expiresAt) {
        this.session = session;
        this.tokenHash = tokenHash;
        this.createdAt = createdAt;
        this.expiresAt = expiresAt;
    }

    public void consume(Instant now, String replacementHash) {
        if (consumedAt != null) throw new IllegalStateException("Refresh token is already consumed");
        consumedAt = now;
        replacedByHash = replacementHash;
    }

    public boolean isExpiredAt(Instant now) { return !now.isBefore(expiresAt); }
    public Long getId() { return id; }
    public DesktopSession getSession() { return session; }
    public String getTokenHash() { return tokenHash; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getExpiresAt() { return expiresAt; }
    public Instant getConsumedAt() { return consumedAt; }
    public String getReplacedByHash() { return replacedByHash; }
}

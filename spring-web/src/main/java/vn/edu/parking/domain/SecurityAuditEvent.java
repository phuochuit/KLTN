package vn.edu.parking.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "security_audit_events")
public class SecurityAuditEvent {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "actor_account_id")
    private Long actorAccountId;

    @Column(name = "actor_username", length = 80)
    private String actorUsername;

    @Column(nullable = false, length = 48)
    private String action;

    @Column(name = "target_type", length = 48)
    private String targetType;

    @Column(name = "target_reference", length = 120)
    private String targetReference;

    @Column(nullable = false, length = 16)
    private String outcome;

    @Column(length = 500)
    private String reason;

    @Column(name = "evidence_reference", length = 500)
    private String evidenceReference;

    @Column(name = "occurred_at", nullable = false, updatable = false)
    private Instant occurredAt;

    protected SecurityAuditEvent() { }

    public SecurityAuditEvent(Long actorAccountId, String actorUsername, String action, String targetType,
            String targetReference, String outcome, String reason, String evidenceReference, Instant occurredAt) {
        this.actorAccountId = actorAccountId;
        this.actorUsername = actorUsername;
        this.action = action;
        this.targetType = targetType;
        this.targetReference = targetReference;
        this.outcome = outcome;
        this.reason = reason;
        this.evidenceReference = evidenceReference;
        this.occurredAt = occurredAt;
    }

    public Long getId() { return id; }
    public Long getActorAccountId() { return actorAccountId; }
    public String getActorUsername() { return actorUsername; }
    public String getAction() { return action; }
    public String getTargetType() { return targetType; }
    public String getTargetReference() { return targetReference; }
    public String getOutcome() { return outcome; }
    public String getReason() { return reason; }
    public String getEvidenceReference() { return evidenceReference; }
    public Instant getOccurredAt() { return occurredAt; }
}

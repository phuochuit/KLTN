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
import java.time.Instant;

@Entity
@Table(name = "gate_shift_assignments")
public class GateShiftAssignment {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "account_id", nullable = false)
    private SystemAccount account;

    @Column(name = "shift_name", nullable = false, length = 80)
    private String shiftName;

    @Column(name = "starts_at", nullable = false)
    private Instant startsAt;

    @Column(name = "ends_at", nullable = false)
    private Instant endsAt;

    @Column(nullable = false, length = 240)
    private String duty;

    @Column(length = 500)
    private String notes;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected GateShiftAssignment() { }

    public GateShiftAssignment(SystemAccount account, String shiftName, Instant startsAt,
            Instant endsAt, String duty, String notes) {
        this.account = account;
        this.shiftName = shiftName;
        this.startsAt = startsAt;
        this.endsAt = endsAt;
        this.duty = duty;
        this.notes = notes;
        this.createdAt = Instant.now();
    }

    public Long getId() { return id; }
    public SystemAccount getAccount() { return account; }
    public String getShiftName() { return shiftName; }
    public Instant getStartsAt() { return startsAt; }
    public Instant getEndsAt() { return endsAt; }
    public String getDuty() { return duty; }
    public String getNotes() { return notes; }
    public Instant getCreatedAt() { return createdAt; }
}

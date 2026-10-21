package vn.edu.parking.domain;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "manual_override_events", indexes = {
    @Index(name = "ix_manual_override_account_created", columnList = "account_id,created_at"),
    @Index(name = "ix_manual_override_parking_session", columnList = "parking_session_id")
})
public class ManualOverrideEvent {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 16) private EvidenceOperation operation;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 16) private ManualOverrideOutcome outcome;
    @Column(name = "reason_code", nullable = false, length = 50) private String reasonCode;
    @Column(name = "reason_text", nullable = false, length = 500) private String reasonText;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "account_id", nullable = false) private SystemAccount account;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "desktop_session_id", nullable = false) private DesktopSession desktopSession;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "gate_shift_assignment_id") private GateShiftAssignment gateShiftAssignment;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "parking_session_id") private ParkingSession parkingSession;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "vehicle_evidence_id", columnDefinition = "char(36)")
    private GateEvidence vehicleEvidence;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "face_evidence_id", columnDefinition = "char(36)")
    private GateEvidence faceEvidence;
    @Column(name = "original_plate_number", length = 30) private String originalPlateNumber;
    @Column(name = "corrected_plate_number", length = 30) private String correctedPlateNumber;
    @Column(name = "original_vehicle_type", length = 50) private String originalVehicleType;
    @Column(name = "corrected_vehicle_type", length = 50) private String correctedVehicleType;
    @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;

    protected ManualOverrideEvent() { }
    public ManualOverrideEvent(EvidenceOperation operation, ManualOverrideOutcome outcome,
            String reasonCode, String reasonText, SystemAccount account, DesktopSession desktopSession,
            GateShiftAssignment shift, ParkingSession parkingSession, GateEvidence vehicleEvidence,
            GateEvidence faceEvidence, Instant createdAt) {
        this(operation, outcome, reasonCode, reasonText, account, desktopSession, shift,
            parkingSession, vehicleEvidence, faceEvidence, null, null, null, null, createdAt);
    }

    public ManualOverrideEvent(EvidenceOperation operation, ManualOverrideOutcome outcome,
            String reasonCode, String reasonText, SystemAccount account, DesktopSession desktopSession,
            GateShiftAssignment shift, ParkingSession parkingSession, GateEvidence vehicleEvidence,
            GateEvidence faceEvidence, String originalPlateNumber, String correctedPlateNumber,
            String originalVehicleType, String correctedVehicleType, Instant createdAt) {
        this.operation = operation; this.outcome = outcome; this.reasonCode = reasonCode;
        this.reasonText = reasonText; this.account = account; this.desktopSession = desktopSession;
        this.gateShiftAssignment = shift; this.parkingSession = parkingSession;
        this.vehicleEvidence = vehicleEvidence; this.faceEvidence = faceEvidence;
        this.originalPlateNumber = originalPlateNumber; this.correctedPlateNumber = correctedPlateNumber;
        this.originalVehicleType = originalVehicleType; this.correctedVehicleType = correctedVehicleType;
        this.createdAt = createdAt;
    }
    public Long getId() { return id; }
    public EvidenceOperation getOperation() { return operation; }
    public ManualOverrideOutcome getOutcome() { return outcome; }
    public String getReasonCode() { return reasonCode; }
    public String getReasonText() { return reasonText; }
    public SystemAccount getAccount() { return account; }
    public DesktopSession getDesktopSession() { return desktopSession; }
    public String getOriginalPlateNumber() { return originalPlateNumber; }
    public String getCorrectedPlateNumber() { return correctedPlateNumber; }
    public String getOriginalVehicleType() { return originalVehicleType; }
    public String getCorrectedVehicleType() { return correctedVehicleType; }
    public Instant getCreatedAt() { return createdAt; }
}

package vn.edu.parking.domain;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "gate_evidences", indexes = {
    @Index(name = "ix_gate_evidence_session_status", columnList = "desktop_session_id,status,expires_at"),
    @Index(name = "ix_gate_evidence_parking_session", columnList = "parking_session_id"),
    @Index(name = "ix_gate_evidence_created", columnList = "created_at"),
    @Index(name = "ix_gate_evidence_expiry", columnList = "status,expires_at"),
    @Index(name = "ix_gate_evidence_request", columnList = "request_id")
})
public class GateEvidence {
    @Id @Column(length = 36, columnDefinition = "char(36)") private String id;
    @Enumerated(EnumType.STRING) @Column(name = "evidence_type", nullable = false, length = 32) private EvidenceType evidenceType;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 16) private EvidenceOperation operation;
    @Enumerated(EnumType.STRING) @Column(name = "source_kind", nullable = false, length = 16) private EvidenceSourceKind sourceKind;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 16) private EvidenceStatus status;
    @Version @Column(nullable = false) private long version;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "account_id", nullable = false) private SystemAccount account;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "desktop_session_id", nullable = false) private DesktopSession desktopSession;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "parking_session_id") private ParkingSession parkingSession;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "family_member_id") private FamilyMember familyMember;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "gate_shift_assignment_id") private GateShiftAssignment gateShiftAssignment;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "consumed_by_account_id") private SystemAccount consumedByAccount;
    @Column(name = "request_id", nullable = false, length = 128) private String requestId;
    @Column(name = "upstream_evidence_id", length = 80) private String upstreamEvidenceId;
    @Column(name = "plate_number", length = 30) private String plateNumber;
    @Column(name = "vehicle_type", length = 50) private String vehicleType;
    @Column(name = "detection_confidence", precision = 8, scale = 6) private BigDecimal detectionConfidence;
    @Column(name = "ocr_confidence", precision = 8, scale = 6) private BigDecimal ocrConfidence;
    @Column(name = "vehicle_confidence", precision = 8, scale = 6) private BigDecimal vehicleConfidence;
    @Column(name = "face_decision", length = 24) private String faceDecision;
    @Column(name = "face_similarity", precision = 8, scale = 6) private BigDecimal faceSimilarity;
    @Column(name = "face_match_threshold", precision = 8, scale = 6) private BigDecimal faceMatchThreshold;
    @Column(name = "face_review_threshold", precision = 8, scale = 6) private BigDecimal faceReviewThreshold;
    @Enumerated(EnumType.STRING) @Column(name = "liveness_status", nullable = false, length = 24) private LivenessStatus livenessStatus;
    @Column(name = "liveness_score", precision = 8, scale = 6) private BigDecimal livenessScore;
    @Column(name = "liveness_provider", length = 80) private String livenessProvider;
    @Column(name = "liveness_model_version", length = 120) private String livenessModelVersion;
    @Column(name = "model_version", nullable = false, length = 160) private String modelVersion;
    @Column(name = "camera_id", length = 120) private String cameraId;
    @Column(name = "frame_index") private Integer frameIndex;
    @Column(name = "request_sha256", length = 64) private String requestSha256;
    @Column(name = "response_sha256", nullable = false, length = 64) private String responseSha256;
    @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;
    @Column(name = "expires_at", nullable = false) private Instant expiresAt;
    @Column(name = "consumed_at") private Instant consumedAt;
    @Column(name = "deleted_at") private Instant deletedAt;

    protected GateEvidence() { }

    public GateEvidence(String id, EvidenceType evidenceType, EvidenceOperation operation,
            EvidenceSourceKind sourceKind, SystemAccount account, DesktopSession desktopSession,
            String requestId, String modelVersion, String responseSha256, Instant createdAt, Instant expiresAt) {
        this.id = id;
        this.evidenceType = evidenceType;
        this.operation = operation;
        this.sourceKind = sourceKind;
        this.account = account;
        this.desktopSession = desktopSession;
        this.requestId = requestId;
        this.modelVersion = modelVersion;
        this.responseSha256 = responseSha256;
        this.createdAt = createdAt;
        this.expiresAt = expiresAt;
        this.status = EvidenceStatus.CREATED;
        this.livenessStatus = LivenessStatus.NOT_EVALUATED;
    }

    public void markReady() {
        if (status != EvidenceStatus.CREATED) throw new IllegalStateException("Evidence không ở trạng thái khởi tạo");
        status = EvidenceStatus.READY;
    }

    public void consume(ParkingSession session, SystemAccount consumer, Instant now) {
        if (status != EvidenceStatus.READY || deletedAt != null || !now.isBefore(expiresAt))
            throw new IllegalStateException("Evidence không còn hợp lệ");
        parkingSession = session;
        consumedByAccount = consumer;
        consumedAt = now;
        status = EvidenceStatus.CONSUMED;
    }

    public void expire(Instant now) {
        if (status == EvidenceStatus.READY && !now.isBefore(expiresAt)) status = EvidenceStatus.EXPIRED;
    }

    public void markDeleted(Instant now) {
        if (deletedAt == null) deletedAt = now;
    }

    public void setVehicleResult(String upstreamId, String plate, String vehicleType,
            Double detection, Double ocr, Double vehicleConfidence, Integer frameIndex) {
        upstreamEvidenceId = upstreamId; plateNumber = plate; this.vehicleType = vehicleType;
        detectionConfidence = decimal(detection); ocrConfidence = decimal(ocr);
        this.vehicleConfidence = decimal(vehicleConfidence);
        this.frameIndex = frameIndex;
    }

    public void setFaceResult(String upstreamId, FamilyMember member, String decision,
            Double similarity, Double matchThreshold, Double reviewThreshold) {
        upstreamEvidenceId = upstreamId; familyMember = member; faceDecision = decision;
        faceSimilarity = decimal(similarity); faceMatchThreshold = decimal(matchThreshold);
        faceReviewThreshold = decimal(reviewThreshold);
    }

    public void setCameraId(String value) { cameraId = value; }
    public void setRequestSha256(String value) { requestSha256 = value; }
    public String getId() { return id; }
    public EvidenceType getEvidenceType() { return evidenceType; }
    public EvidenceOperation getOperation() { return operation; }
    public EvidenceSourceKind getSourceKind() { return sourceKind; }
    public EvidenceStatus getStatus() { return status; }
    public long getVersion() { return version; }
    public SystemAccount getAccount() { return account; }
    public DesktopSession getDesktopSession() { return desktopSession; }
    public ParkingSession getParkingSession() { return parkingSession; }
    public FamilyMember getFamilyMember() { return familyMember; }
    public String getRequestId() { return requestId; }
    public String getUpstreamEvidenceId() { return upstreamEvidenceId; }
    public String getPlateNumber() { return plateNumber; }
    public String getVehicleType() { return vehicleType; }
    public BigDecimal getDetectionConfidence() { return detectionConfidence; }
    public BigDecimal getOcrConfidence() { return ocrConfidence; }
    public BigDecimal getVehicleConfidence() { return vehicleConfidence; }
    public String getFaceDecision() { return faceDecision; }
    public BigDecimal getFaceSimilarity() { return faceSimilarity; }
    public BigDecimal getFaceMatchThreshold() { return faceMatchThreshold; }
    public BigDecimal getFaceReviewThreshold() { return faceReviewThreshold; }
    public LivenessStatus getLivenessStatus() { return livenessStatus; }
    public String getModelVersion() { return modelVersion; }
    public String getCameraId() { return cameraId; }
    public Integer getFrameIndex() { return frameIndex; }
    public String getRequestSha256() { return requestSha256; }
    public String getResponseSha256() { return responseSha256; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getExpiresAt() { return expiresAt; }
    public Instant getConsumedAt() { return consumedAt; }
    public Instant getDeletedAt() { return deletedAt; }

    private static BigDecimal decimal(Double value) {
        return value == null ? null : BigDecimal.valueOf(value);
    }
}

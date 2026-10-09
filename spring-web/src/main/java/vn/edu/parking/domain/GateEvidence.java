package vn.edu.parking.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "gate_evidence")
public class GateEvidence {
    @Id
    @Column(length = 36, nullable = false)
    private String id;

    @Column(name = "owner_account_id", nullable = false)
    private Long ownerAccountId;

    @Column(name = "desktop_session_id", length = 36, nullable = false)
    private String desktopSessionId;

    @Column(name = "parking_session_id")
    private Long parkingSessionId;

    @Enumerated(EnumType.STRING)
    @Column(name = "operation_type", length = 16, nullable = false)
    private GateOperationType operationType;

    @Enumerated(EnumType.STRING)
    @Column(name = "evidence_kind", length = 32, nullable = false)
    private GateEvidenceKind evidenceKind;

    @Column(name = "file_name", length = 64, nullable = false, unique = true)
    private String fileName;

    @Column(name = "derived_file_name", length = 64, unique = true)
    private String derivedFileName;

    @Column(name = "media_type", length = 64, nullable = false)
    private String mediaType;

    @Column(name = "byte_size", nullable = false)
    private long byteSize;

    @Column(name = "sha256", length = 64, nullable = false)
    private String sha256;

    @Column(name = "derived_byte_size")
    private Long derivedByteSize;

    @Column(name = "derived_sha256", length = 64)
    private String derivedSha256;

    @Column(name = "recognized_plate", length = 32)
    private String recognizedPlate;

    @Column(name = "vehicle_type", length = 16)
    private String vehicleType;

    @Column(name = "detection_confidence")
    private Double detectionConfidence;

    @Column(name = "ocr_confidence")
    private Double ocrConfidence;

    @Column(name = "vehicle_confidence")
    private Double vehicleConfidence;

    @Column(name = "verification_decision", length = 16)
    private String verificationDecision;

    @Column(name = "verification_similarity")
    private Double verificationSimilarity;

    @Column(name = "verification_threshold")
    private Double verificationThreshold;

    @Column(name = "family_member_id")
    private Long familyMemberId;

    @Column(name = "frame_index")
    private Integer frameIndex;

    @Column(name = "source_service", length = 48)
    private String sourceService;

    @Column(name = "recognition_status", length = 16)
    private String recognitionStatus;

    @Column(name = "recognition_failure_at")
    private Instant recognitionFailureAt;

    @Column(name = "face_verification_status", length = 16)
    private String faceVerificationStatus;

    @Column(name = "face_verification_failure_at")
    private Instant faceVerificationFailureAt;

    @Column(name = "face_verification_evidence_id", length = 36)
    private String faceVerificationEvidenceId;

    @Column(name = "captured_at", nullable = false)
    private Instant capturedAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "recognition_valid_until")
    private Instant recognitionValidUntil;

    @Column(name = "consumed_at")
    private Instant consumedAt;

    @Column(name = "preserve_until")
    private Instant preserveUntil;

    @Column(name = "preserve_reason", length = 500)
    private String preserveReason;

    @Column(name = "preserve_approved_by")
    private Long preserveApprovedBy;

    @Column(name = "preserve_approved_at")
    private Instant preserveApprovedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected GateEvidence() { }

    public GateEvidence(String id, Long ownerAccountId, String desktopSessionId, GateOperationType operationType,
            GateEvidenceKind evidenceKind, String fileName, String mediaType, long byteSize, String sha256,
            Instant capturedAt, Instant expiresAt, Instant createdAt) {
        this.id = id;
        this.ownerAccountId = ownerAccountId;
        this.desktopSessionId = desktopSessionId;
        this.operationType = operationType;
        this.evidenceKind = evidenceKind;
        this.fileName = fileName;
        this.mediaType = mediaType;
        this.byteSize = byteSize;
        this.sha256 = sha256;
        this.capturedAt = capturedAt;
        this.expiresAt = expiresAt;
        this.createdAt = createdAt;
        if (evidenceKind == GateEvidenceKind.AI_RECOGNITION) recognitionStatus = "PENDING";
    }

    public void bindToParkingSession(Long sessionId) {
        if (sessionId == null || (parkingSessionId != null && !parkingSessionId.equals(sessionId)))
            throw new IllegalStateException("Gate evidence cannot be rebound to another parking session");
        parkingSessionId = sessionId;
    }

    public void setRecognitionValidUntil(Instant validUntil) { this.recognitionValidUntil = validUntil; }

    public void consume(Instant at) {
        if (at == null || consumedAt != null) throw new IllegalStateException("Gate evidence is already consumed");
        consumedAt = at;
    }

    public void setRecognition(String plate, String type, Double detection, Double ocr, Double vehicle,
            String derivedFilename, Long derivedSize, String derivedDigest, Integer frame, String service) {
        recognizedPlate = plate;
        vehicleType = type;
        detectionConfidence = detection;
        ocrConfidence = ocr;
        vehicleConfidence = vehicle;
        derivedFileName = derivedFilename;
        derivedByteSize = derivedSize;
        derivedSha256 = derivedDigest;
        frameIndex = frame;
        sourceService = service;
        recognitionStatus = "SUCCESS";
        recognitionFailureAt = null;
    }

    public void setSourceService(String service) { sourceService = service; }

    public void markRecognitionUnavailable(Instant at) {
        if (!"PENDING".equals(recognitionStatus) || at == null)
            throw new IllegalStateException("Only pending recognition can be marked unavailable");
        recognitionStatus = "UNAVAILABLE";
        recognitionFailureAt = at;
    }

    public void markRecognitionFailed(Instant at) {
        if (!"PENDING".equals(recognitionStatus) || at == null)
            throw new IllegalStateException("Only pending recognition can be marked failed");
        recognitionStatus = "FAILED";
        recognitionFailureAt = at;
    }

    public void markFaceVerificationUnavailable(Instant at) {
        if (evidenceKind != GateEvidenceKind.AI_RECOGNITION
                || !("SUCCESS".equals(recognitionStatus) || "UNAVAILABLE".equals(recognitionStatus))
                || (faceVerificationStatus != null && !"UNAVAILABLE".equals(faceVerificationStatus)) || at == null)
            throw new IllegalStateException("Face verification failure must belong to an unused recognized operation");
        faceVerificationStatus = "UNAVAILABLE";
        faceVerificationFailureAt = at;
    }

    public void markFaceVerificationFailed(Instant at) {
        if (evidenceKind != GateEvidenceKind.AI_RECOGNITION
                || !("SUCCESS".equals(recognitionStatus) || "UNAVAILABLE".equals(recognitionStatus))
                || at == null)
            throw new IllegalStateException("Face verification failure must belong to a recognized operation");
        faceVerificationStatus = "FAILED";
        faceVerificationFailureAt = at;
        faceVerificationEvidenceId = null;
    }

    public void setFaceVerificationResult(String decision, String evidenceId) {
        if (evidenceKind != GateEvidenceKind.AI_RECOGNITION
                || !("SUCCESS".equals(recognitionStatus) || "UNAVAILABLE".equals(recognitionStatus))
                || !java.util.Set.of("PASS", "REVIEW", "REJECT").contains(decision)
                || evidenceId == null || evidenceId.isBlank())
            throw new IllegalStateException("Face result must belong to a recognized operation");
        faceVerificationStatus = decision;
        faceVerificationFailureAt = null;
        faceVerificationEvidenceId = evidenceId;
    }

    public void setFaceVerification(String plate, String decision, Double similarity, Double threshold,
            Long memberId, Integer frame, String service) {
        recognizedPlate = plate;
        verificationDecision = decision;
        verificationSimilarity = similarity;
        verificationThreshold = threshold;
        familyMemberId = memberId;
        frameIndex = frame;
        sourceService = service;
    }

    public void approvePreservation(Long approverAccountId, Instant until, String reason, Instant approvedAt) {
        if (approverAccountId == null || until == null || !until.isAfter(approvedAt)
                || reason == null || reason.isBlank() || reason.length() > 500) {
            throw new IllegalArgumentException("A reason and finite future preservation deadline are required");
        }
        preserveApprovedBy = approverAccountId;
        preserveUntil = until;
        preserveReason = reason.trim();
        preserveApprovedAt = approvedAt;
    }

    public void releasePreservation() {
        preserveUntil = null;
        preserveReason = null;
        preserveApprovedBy = null;
        preserveApprovedAt = null;
    }

    public String getId() { return id; }
    public Long getOwnerAccountId() { return ownerAccountId; }
    public String getDesktopSessionId() { return desktopSessionId; }
    public Long getParkingSessionId() { return parkingSessionId; }
    public GateOperationType getOperationType() { return operationType; }
    public GateEvidenceKind getEvidenceKind() { return evidenceKind; }
    public String getFileName() { return fileName; }
    public String getDerivedFileName() { return derivedFileName; }
    public String getMediaType() { return mediaType; }
    public long getByteSize() { return byteSize; }
    public String getSha256() { return sha256; }
    public Long getDerivedByteSize() { return derivedByteSize; }
    public String getDerivedSha256() { return derivedSha256; }
    public String getRecognizedPlate() { return recognizedPlate; }
    public String getVehicleType() { return vehicleType; }
    public Double getDetectionConfidence() { return detectionConfidence; }
    public Double getOcrConfidence() { return ocrConfidence; }
    public Double getVehicleConfidence() { return vehicleConfidence; }
    public String getVerificationDecision() { return verificationDecision; }
    public Double getVerificationSimilarity() { return verificationSimilarity; }
    public Double getVerificationThreshold() { return verificationThreshold; }
    public Long getFamilyMemberId() { return familyMemberId; }
    public Integer getFrameIndex() { return frameIndex; }
    public String getSourceService() { return sourceService; }
    public String getRecognitionStatus() { return recognitionStatus; }
    public Instant getRecognitionFailureAt() { return recognitionFailureAt; }
    public String getFaceVerificationStatus() { return faceVerificationStatus; }
    public Instant getFaceVerificationFailureAt() { return faceVerificationFailureAt; }
    public String getFaceVerificationEvidenceId() { return faceVerificationEvidenceId; }
    public Instant getCapturedAt() { return capturedAt; }
    public Instant getExpiresAt() { return expiresAt; }
    public Instant getRecognitionValidUntil() { return recognitionValidUntil; }
    public Instant getConsumedAt() { return consumedAt; }
    public Instant getPreserveUntil() { return preserveUntil; }
    public String getPreserveReason() { return preserveReason; }
    public Long getPreserveApprovedBy() { return preserveApprovedBy; }
    public Instant getPreserveApprovedAt() { return preserveApprovedAt; }
    public Instant getCreatedAt() { return createdAt; }
}

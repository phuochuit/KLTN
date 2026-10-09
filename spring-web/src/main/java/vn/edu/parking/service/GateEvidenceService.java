package vn.edu.parking.service;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import vn.edu.parking.domain.GateEvidence;
import vn.edu.parking.domain.GateEvidenceKind;
import vn.edu.parking.domain.GateOperationType;
import vn.edu.parking.domain.ParkingSession;
import vn.edu.parking.domain.SessionStatus;
import vn.edu.parking.repository.GateEvidenceRepository;
import vn.edu.parking.repository.ParkingSessionRepository;

import java.io.IOException;
import java.io.ByteArrayInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import javax.imageio.ImageIO;

@Service
public class GateEvidenceService {
    private static final int MAX_IMAGE_BYTES = 10 * 1024 * 1024;
    private static final Duration RECOGNITION_USE_WINDOW = Duration.ofMinutes(5);
    private static final Set<String> IMAGE_TYPES = Set.of("image/jpeg", "image/png", "image/bmp", "image/webp");
    private static final Set<String> VIDEO_TYPES = Set.of(".mp4", ".avi", ".mov", ".mkv", ".wmv", ".m4v", ".webm");
    private static final Pattern EVIDENCE_ID = Pattern.compile(
        "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}");
    private final GateEvidenceRepository evidence;
    private final ParkingSessionRepository sessions;
    private final GateEvidenceStorage storage;
    private final GateEvidencePolicy policy;
    private final SecurityAuditService audit;

    public GateEvidenceService(GateEvidenceRepository evidence, ParkingSessionRepository sessions,
            GateEvidenceStorage storage,
            GateEvidencePolicy policy, SecurityAuditService audit) {
        this.evidence = evidence;
        this.sessions = sessions;
        this.storage = storage;
        this.policy = policy;
        this.audit = audit;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public String recordImageCapture(byte[] image, String mediaType, GateOperationType operationType,
            Authentication authentication) {
        Actor actor = actor(authentication);
        if (!IMAGE_TYPES.contains(mediaType) || image == null || image.length == 0 || image.length > MAX_IMAGE_BYTES)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid gate evidence image");
        if (operationType == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Operation type is required");

        Instant capturedAt = Instant.now();
        String filename;
        try {
            filename = storage.store(image, MAX_IMAGE_BYTES);
        } catch (IOException ex) {
            throw new ResponseStatusException(HttpStatus.INSUFFICIENT_STORAGE, "Unable to store gate evidence");
        }
        String id = UUID.randomUUID().toString();
        GateEvidence item = new GateEvidence(id, actor.accountId(), actor.sessionId(), operationType,
            GateEvidenceKind.AI_RECOGNITION, filename, mediaType, image.length, sha256(image), capturedAt,
            policy.expiresAt(capturedAt), capturedAt);
        item.setSourceService("fastapi-image");
        evidence.saveAndFlush(item);
        audit.record(authentication, "GATE_EVIDENCE_CAPTURED", "GATE_EVIDENCE", id,
            "SUCCESS", null, id);
        return id;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordRecognition(String evidenceId, Authentication authentication, JsonNode response) {
        if (response == null || !response.isObject())
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "ANPR service returned an invalid response");
        GateEvidence item = ownedByCurrentSession(evidenceId, authentication);
        String annotatedImage = response.path("annotatedImageBase64").asText("");
        String derivedFilename = null;
        Long derivedSize = null;
        String derivedDigest = null;
        if (!annotatedImage.isEmpty()) {
            byte[] derived = decodeAnnotatedImage(annotatedImage);
            try {
                derivedFilename = storage.store(derived, MAX_IMAGE_BYTES);
            } catch (IOException ex) {
                throw new ResponseStatusException(HttpStatus.INSUFFICIENT_STORAGE,
                    "Unable to store derived gate evidence");
            }
            derivedSize = (long) derived.length;
            derivedDigest = sha256(derived);
        }
        item.setRecognition(response.path("plateText").asText(""), response.path("vehicleType").asText("UNKNOWN"),
            response.path("detectionConfidence").doubleValue(), response.path("ocrConfidence").doubleValue(),
            response.path("vehicleConfidence").doubleValue(), derivedFilename, derivedSize, derivedDigest,
            response.path("frameIndex").intValue(), "fastapi-image");
        item.setRecognitionValidUntil(Instant.now().plus(RECOGNITION_USE_WINDOW));
        evidence.saveAndFlush(item);
        audit.record(authentication, "GATE_EVIDENCE_RECOGNIZED", "GATE_EVIDENCE", evidenceId,
            "SUCCESS", null, evidenceId);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public String recordVideoRecognition(byte[] annotatedImage, GateOperationType operationType,
            Authentication authentication, JsonNode response, Instant capturedAt) {
        Actor actor = actor(authentication);
        if (operationType == null || response == null || !response.isObject() || !isDecodableJpeg(annotatedImage))
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "ANPR service returned an invalid video result");
        String filename;
        try {
            filename = storage.store(annotatedImage, MAX_IMAGE_BYTES);
        } catch (IOException ex) {
            throw new ResponseStatusException(HttpStatus.INSUFFICIENT_STORAGE, "Unable to store video evidence");
        }
        Instant recognizedAt = Instant.now();
        String id = UUID.randomUUID().toString();
        GateEvidence item = new GateEvidence(id, actor.accountId(), actor.sessionId(), operationType,
            GateEvidenceKind.AI_RECOGNITION, filename, "image/jpeg", annotatedImage.length, sha256(annotatedImage),
            capturedAt, policy.expiresAt(capturedAt), recognizedAt);
        item.setRecognition(response.path("plateText").asText(""), response.path("vehicleType").asText("UNKNOWN"),
            response.path("detectionConfidence").doubleValue(), response.path("ocrConfidence").doubleValue(),
            response.path("vehicleConfidence").doubleValue(), null, null, null,
            response.path("frameIndex").intValue(), "fastapi-video");
        item.setRecognitionValidUntil(recognizedAt.plus(RECOGNITION_USE_WINDOW));
        evidence.saveAndFlush(item);
        audit.record(authentication, "GATE_EVIDENCE_CAPTURED", "GATE_EVIDENCE", id,
            "SUCCESS", "VIDEO_RECOGNITION_FRAME", id);
        audit.record(authentication, "GATE_EVIDENCE_RECOGNIZED", "GATE_EVIDENCE", id,
            "SUCCESS", null, id);
        return id;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public String recordVideoFailure(byte[] video, String extension, GateOperationType operationType,
            Authentication authentication, Instant capturedAt) {
        Actor actor = actor(authentication);
        if (video == null || video.length == 0 || video.length > MAX_IMAGE_BYTES
                || !VIDEO_TYPES.contains(extension) || operationType == null || capturedAt == null)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid gate video evidence");
        String mediaType = switch (extension) {
            case ".mp4", ".m4v" -> "video/mp4";
            case ".mov" -> "video/quicktime";
            case ".avi" -> "video/x-msvideo";
            case ".mkv" -> "video/x-matroska";
            case ".wmv" -> "video/x-ms-wmv";
            case ".webm" -> "video/webm";
            default -> throw new ResponseStatusException(HttpStatus.UNSUPPORTED_MEDIA_TYPE);
        };
        String filename;
        try {
            filename = storage.store(video, MAX_IMAGE_BYTES);
        } catch (IOException ex) {
            throw new ResponseStatusException(HttpStatus.INSUFFICIENT_STORAGE, "Unable to store gate video evidence");
        }
        Instant now = Instant.now();
        String id = UUID.randomUUID().toString();
        GateEvidence item = new GateEvidence(id, actor.accountId(), actor.sessionId(), operationType,
            GateEvidenceKind.AI_RECOGNITION, filename, mediaType, video.length, sha256(video), capturedAt,
            policy.expiresAt(capturedAt), now);
        item.setSourceService("fastapi-video");
        item.markRecognitionUnavailable(now);
        evidence.saveAndFlush(item);
        audit.record(authentication, "GATE_EVIDENCE_CAPTURED", "GATE_EVIDENCE", id,
            "SUCCESS", "VIDEO_RECOGNITION_UNAVAILABLE", id);
        audit.record(authentication, "GATE_EVIDENCE_RECOGNITION_FAILURE", "GATE_EVIDENCE", id,
            "FAILURE", "RECOGNITION_SERVICE_UNAVAILABLE", id);
        return id;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordRecognitionFailure(String evidenceId, Authentication authentication) {
        if (evidenceId != null) {
            GateEvidence item = ownedByCurrentSession(evidenceId, authentication);
            if ("PENDING".equals(item.getRecognitionStatus())) {
                item.markRecognitionFailed(Instant.now());
                evidence.save(item);
            }
        }
        audit.record(authentication, "GATE_EVIDENCE_RECOGNITION_FAILURE", "GATE_EVIDENCE", evidenceId,
            "FAILURE", "ANPR_UPSTREAM_FAILURE", evidenceId);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordRecognitionUnavailable(String evidenceId, Authentication authentication) {
        GateEvidence item = ownedByCurrentSession(evidenceId, authentication);
        item.markRecognitionUnavailable(Instant.now());
        evidence.saveAndFlush(item);
        audit.record(authentication, "GATE_EVIDENCE_RECOGNITION_FAILURE", "GATE_EVIDENCE", evidenceId,
            "FAILURE", "RECOGNITION_SERVICE_UNAVAILABLE", evidenceId);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public String recordFaceCapture(byte[] image, String plate, Authentication authentication) {
        return storeFaceEvidence(image, plate, "CAPTURED", 0.0, null, null,
            GateOperationType.ENTRY, null, authentication);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public String recordFaceVerification(String recognitionEvidenceId, byte[] image, String plate,
            Long familyMemberId, GateOperationType operation, Long parkingSessionId,
            Authentication authentication, JsonNode response) {
        if (response == null || !response.isObject())
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "ANPR service returned an invalid face response");
        String decision = response.path("decision").asText("");
        double similarity = response.path("similarity").asDouble(Double.NaN);
        double threshold = response.path("matchThreshold").asDouble(Double.NaN);
        if (!Set.of("PASS", "REVIEW", "REJECT").contains(decision)
                || !Double.isFinite(similarity) || similarity < 0 || similarity > 1
                || !Double.isFinite(threshold) || threshold < 0 || threshold > 1
                || (operation == GateOperationType.EXIT && parkingSessionId == null)
                || (operation == GateOperationType.ENTRY && parkingSessionId != null))
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "ANPR service returned an invalid face response");
        GateEvidence operationEvidence = lockOwnedByCurrentSession(recognitionEvidenceId, authentication);
        if ("UNAVAILABLE".equals(operationEvidence.getRecognitionStatus()))
            validateUnavailableEvidence(operationEvidence, operation, Instant.now(), parkingSessionId);
        else
            validateRecognition(operationEvidence, operation, plate, Instant.now());
        if (PlateNormalizer.normalize(plate).isBlank())
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A valid plate is required");
        if (operation == GateOperationType.ENTRY && operationEvidence.getParkingSessionId() != null)
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Entry evidence has already been used");
        if (operation == GateOperationType.EXIT && (parkingSessionId == null
                || (operationEvidence.getParkingSessionId() != null
                    && !operationEvidence.getParkingSessionId().equals(parkingSessionId))))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Exit evidence does not match the open session");
        String faceEvidenceId = storeFaceEvidence(image, plate, decision, similarity, threshold, familyMemberId,
            operation, parkingSessionId, authentication);
        operationEvidence.setFaceVerificationResult(decision, faceEvidenceId);
        evidence.saveAndFlush(operationEvidence);
        return faceEvidenceId;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordFaceVerificationFailure(String recognitionEvidenceId, Authentication authentication) {
        GateEvidence item = lockOwnedByCurrentSession(recognitionEvidenceId, authentication);
        item.markFaceVerificationFailed(Instant.now());
        evidence.saveAndFlush(item);
        audit.record(authentication, "GATE_EVIDENCE_FACE_FAILURE", "GATE_EVIDENCE", item.getId(),
            "FAILURE", "ANPR_INVALID_RESPONSE", item.getId());
    }

    public void recordFaceVerificationFailure(Authentication authentication) {
        audit.record(authentication, "GATE_EVIDENCE_FACE_FAILURE", "GATE_EVIDENCE", null,
            "FAILURE", "ANPR_UPSTREAM_FAILURE", null);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordFaceVerificationUnavailable(String recognitionEvidenceId, String plate,
            GateOperationType operation, Long parkingSessionId, Authentication authentication) {
        GateEvidence item = lockOwnedByCurrentSession(recognitionEvidenceId, authentication);
        Instant now = Instant.now();
        if (operation == GateOperationType.ENTRY || operation == GateOperationType.EXIT) {
            if ("UNAVAILABLE".equals(item.getRecognitionStatus()))
                validateUnavailableEvidence(item, operation, now, parkingSessionId);
            else
                validateRecognition(item, operation, plate, now);
            if (PlateNormalizer.normalize(plate).isBlank())
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A valid plate is required");
        } else {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported gate operation");
        }
        if (operation == GateOperationType.ENTRY) {
            if (item.getParkingSessionId() != null)
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Entry evidence has already been used");
        } else {
            if (parkingSessionId == null || (item.getParkingSessionId() != null
                    && !item.getParkingSessionId().equals(parkingSessionId)))
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Exit evidence does not match the open session");
        }
        item.markFaceVerificationUnavailable(now);
        evidence.saveAndFlush(item);
        audit.record(authentication, "GATE_EVIDENCE_FACE_FAILURE", "GATE_EVIDENCE", item.getId(),
            "FAILURE", "RECOGNITION_SERVICE_UNAVAILABLE", item.getId());
    }

    @Transactional
    public FaceProof requireEntryFaceEvidence(String evidenceId, String recognitionEvidenceId, String plate,
            Long familyMemberId, boolean allowReview, Authentication authentication) {
        GateEvidence item = lockOwnedByCurrentSession(evidenceId, authentication);
        String decision = item.getVerificationDecision();
        boolean guestCapture = familyMemberId == null && "CAPTURED".equals(decision);
        if (!(guestCapture || "PASS".equals(decision) || (allowReview && "REVIEW".equals(decision))))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Face result is not eligible for this operation");
        validateFaceEvidence(item, GateOperationType.ENTRY, plate, decision, Instant.now());
        if (item.getParkingSessionId() != null || item.getConsumedAt() != null
                || !java.util.Objects.equals(item.getFamilyMemberId(), familyMemberId))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Face evidence has already been used or does not match");
        if (!guestCapture) validateLinkedFaceEvidence(recognitionEvidenceId, item, GateOperationType.ENTRY,
            plate, authentication);
        return new FaceProof(item.getId(), item.getVerificationDecision(), item.getVerificationSimilarity(),
            item.getFamilyMemberId());
    }

    @Transactional
    public void bindEntryFaceEvidence(String evidenceId, String recognitionEvidenceId, String plate,
            Long familyMemberId, Long parkingSessionId, Authentication authentication) {
        GateEvidence item = lockOwnedByCurrentSession(evidenceId, authentication);
        String expectedDecision = item.getVerificationDecision();
        boolean guestCapture = familyMemberId == null && "CAPTURED".equals(expectedDecision);
        if (!(guestCapture || "PASS".equals(expectedDecision) || "REVIEW".equals(expectedDecision)))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Face result is not eligible for this operation");
        validateFaceEvidence(item, GateOperationType.ENTRY, plate, expectedDecision, Instant.now());
        if (item.getParkingSessionId() != null || item.getConsumedAt() != null
                || !java.util.Objects.equals(item.getFamilyMemberId(), familyMemberId))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Face evidence has already been used or does not match");
        if (!guestCapture) validateLinkedFaceEvidence(recognitionEvidenceId, item, GateOperationType.ENTRY,
            plate, authentication);
        item.bindToParkingSession(parkingSessionId);
        item.consume(Instant.now());
        evidence.save(item);
        audit.record(authentication, "GATE_EVIDENCE_BOUND", "PARKING_SESSION", parkingSessionId.toString(),
            "SUCCESS", "ENTRY_FACE_" + expectedDecision, evidenceId);
    }

    @Transactional
    public FaceProof requireExitFaceEvidence(String evidenceId, String recognitionEvidenceId, String plate,
            Long parkingSessionId, Long familyMemberId, boolean allowReview, Authentication authentication) {
        GateEvidence item = lockOwnedByCurrentSession(evidenceId, authentication);
        String decision = item.getVerificationDecision();
        if (!"PASS".equals(decision) && !(allowReview && "REVIEW".equals(decision)))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Face result is not eligible for this operation");
        validateFaceEvidence(item, GateOperationType.EXIT, plate, decision, Instant.now());
        if (!java.util.Objects.equals(item.getParkingSessionId(), parkingSessionId)
                || !java.util.Objects.equals(item.getFamilyMemberId(), familyMemberId)
                || item.getConsumedAt() != null)
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Face evidence does not belong to this exit operation");
        validateLinkedFaceEvidence(recognitionEvidenceId, item, GateOperationType.EXIT, plate, authentication);
        return new FaceProof(item.getId(), item.getVerificationDecision(), item.getVerificationSimilarity(),
            item.getFamilyMemberId());
    }

    @Transactional
    public void consumeExitFaceEvidence(String evidenceId, String recognitionEvidenceId, String plate,
            Long parkingSessionId, Long familyMemberId, Authentication authentication) {
        GateEvidence item = lockOwnedByCurrentSession(evidenceId, authentication);
        String decision = item.getVerificationDecision();
        if (!"PASS".equals(decision) && !"REVIEW".equals(decision))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Face result is not eligible for this operation");
        validateFaceEvidence(item, GateOperationType.EXIT, plate, decision, Instant.now());
        if (!java.util.Objects.equals(item.getParkingSessionId(), parkingSessionId)
                || !java.util.Objects.equals(item.getFamilyMemberId(), familyMemberId)
                || item.getConsumedAt() != null)
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Face evidence does not belong to this exit operation");
        validateLinkedFaceEvidence(recognitionEvidenceId, item, GateOperationType.EXIT, plate, authentication);
        item.consume(Instant.now());
        evidence.save(item);
        audit.record(authentication, "GATE_EVIDENCE_BOUND", "PARKING_SESSION", parkingSessionId.toString(),
            "SUCCESS", "EXIT_FACE_VERIFICATION", evidenceId);
    }

    public EvidenceImage readGuestEntryFaceImage(String evidenceReference, Long parkingSessionId,
            Authentication authentication) {
        String prefix = "gate-evidence:";
        String evidenceId = evidenceReference != null && evidenceReference.startsWith(prefix)
            ? evidenceReference.substring(prefix.length()) : null;
        GateEvidence item = evidenceId != null && EVIDENCE_ID.matcher(evidenceId).matches()
            ? evidence.findById(evidenceId).orElse(null) : null;
        Instant now = Instant.now();
        if (item == null || parkingSessionId == null || !sessions.existsById(parkingSessionId)
                || !java.util.Objects.equals(item.getParkingSessionId(), parkingSessionId)
                || item.getEvidenceKind() != GateEvidenceKind.FACE_VERIFICATION
                || item.getOperationType() != GateOperationType.ENTRY
                || !"CAPTURED".equals(item.getVerificationDecision()) || !item.getExpiresAt().isAfter(now)) {
            audit.record(authentication, "GATE_EVIDENCE_READ", "PARKING_SESSION", parkingSessionId == null
                ? null : parkingSessionId.toString(), "FAILURE", "EXIT:GUEST_ENTRY_FACE_IMAGE",
                safeReference(evidenceId));
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Gate evidence is unavailable");
        }
        try {
            byte[] bytes = storage.read(item.getFileName(), MAX_IMAGE_BYTES);
            audit.record(authentication, "GATE_EVIDENCE_READ", "PARKING_SESSION", parkingSessionId.toString(),
                "SUCCESS", "EXIT:GUEST_ENTRY_FACE_IMAGE", item.getId());
            return new EvidenceImage(bytes, item.getMediaType());
        } catch (IOException | IllegalArgumentException ex) {
            audit.record(authentication, "GATE_EVIDENCE_READ", "PARKING_SESSION", parkingSessionId.toString(),
                "FAILURE", "EXIT:GUEST_ENTRY_FACE_IMAGE", item.getId());
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Gate evidence is unavailable");
        }
    }

    @Transactional
    public Recognition requireEntryRecognition(String evidenceId, String plate, Authentication authentication) {
        return requireEntryRecognition(evidenceId, plate, authentication, false);
    }

    @Transactional
    public Recognition requireEntryRecognition(String evidenceId, String plate, Authentication authentication,
            boolean allowUnavailable) {
        GateEvidence item = lockOwnedByCurrentSession(evidenceId, authentication);
        Instant now = Instant.now();
        boolean recognitionUnavailable = "UNAVAILABLE".equals(item.getRecognitionStatus());
        boolean faceUnavailable = "UNAVAILABLE".equals(item.getFaceVerificationStatus());
        if (recognitionUnavailable) {
            if (!allowUnavailable)
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Manual override is required for unavailable recognition");
            validateUnavailableEvidence(item, GateOperationType.ENTRY, now, null);
            if (PlateNormalizer.normalize(plate).isBlank())
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A manual plate is required");
        } else {
            validateRecognition(item, GateOperationType.ENTRY, plate, now);
        }
        if (item.getParkingSessionId() != null || item.getConsumedAt() != null)
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Gate evidence has already been used");
        if ((recognitionUnavailable || faceUnavailable) && !allowUnavailable)
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Manual override is required for unavailable recognition");
        if ("REJECT".equals(item.getFaceVerificationStatus()))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Backend face verification rejected the operation");
        if (allowUnavailable && (recognitionUnavailable || faceUnavailable)) requireOverrideAuthority(authentication);
        return new Recognition(recognitionUnavailable ? PlateNormalizer.normalize(plate)
                : PlateNormalizer.normalize(item.getRecognizedPlate()),
            recognitionUnavailable ? "UNKNOWN" : item.getVehicleType(), recognitionUnavailable,
            item.getFaceVerificationStatus(), item.getFaceVerificationEvidenceId(), item.getId());
    }

    @Transactional
    public void bindEntryToParkingSession(String evidenceId, Long parkingSessionId, Authentication authentication) {
        GateEvidence item = lockOwnedByCurrentSession(evidenceId, authentication);
        validateEvidenceForBinding(item, GateOperationType.ENTRY, item.getRecognizedPlate(), null, Instant.now());
        if (item.getParkingSessionId() != null || item.getConsumedAt() != null)
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Gate evidence has already been used");
        item.bindToParkingSession(parkingSessionId);
        item.consume(Instant.now());
        evidence.save(item);
        audit.record(authentication, "GATE_EVIDENCE_BOUND", "PARKING_SESSION", parkingSessionId.toString(),
            "SUCCESS", "ENTRY_RECOGNITION", evidenceId);
    }

    @Transactional
    public void validateExitRecognition(String evidenceId, String plate, Authentication authentication) {
        requireExitRecognition(evidenceId, plate, null, authentication, false);
    }

    @Transactional
    public Recognition requireExitRecognition(String evidenceId, String plate, Long parkingSessionId,
            Authentication authentication, boolean allowUnavailable) {
        GateEvidence item = lockOwnedByCurrentSession(evidenceId, authentication);
        Instant now = Instant.now();
        boolean recognitionUnavailable = "UNAVAILABLE".equals(item.getRecognitionStatus());
        boolean faceUnavailable = "UNAVAILABLE".equals(item.getFaceVerificationStatus());
        if (recognitionUnavailable) {
            if (!allowUnavailable)
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Manual override is required for unavailable recognition");
            validateUnavailableEvidence(item, GateOperationType.EXIT, now, parkingSessionId);
            if (PlateNormalizer.normalize(plate).isBlank())
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A manual plate is required");
        } else {
            validateRecognition(item, GateOperationType.EXIT, plate, now);
        }
        if ((recognitionUnavailable || faceUnavailable) && !allowUnavailable)
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Manual override is required for unavailable recognition");
        if (parkingSessionId != null && item.getParkingSessionId() != null
                && !item.getParkingSessionId().equals(parkingSessionId))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Gate evidence belongs to another parking session");
        if ("REJECT".equals(item.getFaceVerificationStatus()))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Backend face verification rejected the operation");
        if (allowUnavailable && (recognitionUnavailable || faceUnavailable)) requireOverrideAuthority(authentication);
        return new Recognition(recognitionUnavailable ? PlateNormalizer.normalize(plate)
                : PlateNormalizer.normalize(item.getRecognizedPlate()),
            recognitionUnavailable ? "UNKNOWN" : item.getVehicleType(), recognitionUnavailable,
            item.getFaceVerificationStatus(), item.getFaceVerificationEvidenceId(), item.getId());
    }

    @Transactional
    public void bindExitToParkingSession(String evidenceId, String plate, Long parkingSessionId,
            Authentication authentication) {
        GateEvidence item = lockOwnedByCurrentSession(evidenceId, authentication);
        validateEvidenceForBinding(item, GateOperationType.EXIT, plate, parkingSessionId, Instant.now());
        if (item.getConsumedAt() != null || (item.getParkingSessionId() != null
                && !item.getParkingSessionId().equals(parkingSessionId)))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Gate evidence belongs to another operation");
        item.bindToParkingSession(parkingSessionId);
        evidence.save(item);
    }

    @Transactional
    public void consumeExitRecognition(String evidenceId, String plate, Long parkingSessionId,
            Authentication authentication) {
        GateEvidence item = lockOwnedByCurrentSession(evidenceId, authentication);
        validateEvidenceForBinding(item, GateOperationType.EXIT, plate, parkingSessionId, Instant.now());
        if (!parkingSessionId.equals(item.getParkingSessionId()) || item.getConsumedAt() != null)
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Gate evidence was not prepared for this operation");
        item.consume(Instant.now());
        evidence.save(item);
        audit.record(authentication, "GATE_EVIDENCE_BOUND", "PARKING_SESSION", parkingSessionId.toString(),
            "SUCCESS", "EXIT_RECOGNITION", evidenceId);
    }

    public EvidenceImage readImage(Long parkingSessionId, GateOperationType operation,
            GateEvidenceKind evidenceKind, String evidenceId, boolean derived, Authentication authentication) {
        Actor actor = actor(authentication);
        GateEvidence item = evidence.findById(evidenceId).orElse(null);
        ParkingSession session = parkingSessionId == null ? null : sessions.findById(parkingSessionId).orElse(null);
        Instant now = Instant.now();
        boolean expiredWithoutActiveHold = item != null && !item.getExpiresAt().isAfter(now)
            && (item.getPreserveUntil() == null || !item.getPreserveUntil().isAfter(now));
        boolean matchingOperation = item != null && operation != null && evidenceKind != null
            && java.util.Objects.equals(item.getParkingSessionId(), parkingSessionId)
            && item.getOperationType() == operation
            && item.getEvidenceKind() == evidenceKind
            && IMAGE_TYPES.contains(item.getMediaType());
        if (item == null || session == null || !matchingOperation || expiredWithoutActiveHold
                || !canRead(item, actor, authentication, session)) {
            auditEvidenceRead(authentication, parkingSessionId, operation, evidenceId, derived, "FAILURE");
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Gate evidence is unavailable");
        }

        String filename = derived ? item.getDerivedFileName() : item.getFileName();
        if (filename == null) {
            auditEvidenceRead(authentication, parkingSessionId, operation, item.getId(), derived, "FAILURE");
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Gate evidence is unavailable");
        }
        try {
            byte[] bytes = storage.read(filename, MAX_IMAGE_BYTES);
            auditEvidenceRead(authentication, parkingSessionId, operation, item.getId(), derived, "SUCCESS");
            return new EvidenceImage(bytes, derived ? "image/jpeg" : item.getMediaType());
        } catch (IOException | IllegalArgumentException ex) {
            auditEvidenceRead(authentication, parkingSessionId, operation, item.getId(), derived, "FAILURE");
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Gate evidence is unavailable");
        }
    }

    @Transactional
    public void approvePreservation(Long parkingSessionId, GateOperationType operation, String evidenceId,
            Instant preserveUntil, String reason, Authentication authentication) {
        Actor actor = actor(authentication);
        if (!isManagement(authentication)) throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        GateEvidence item = requireSessionEvidence(parkingSessionId, operation, evidenceId);
        if (!item.getExpiresAt().isAfter(Instant.now())
                && (item.getPreserveUntil() == null || !item.getPreserveUntil().isAfter(Instant.now())))
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        if (reason == null || reason.isBlank() || reason.trim().length() > 400)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A preservation reason is required");
        Instant approvedAt = Instant.now().truncatedTo(ChronoUnit.MICROS);
        Instant normalizedDeadline = preserveUntil == null ? null : preserveUntil.truncatedTo(ChronoUnit.MICROS);
        try {
            item.approvePreservation(actor.accountId(), normalizedDeadline, reason, approvedAt);
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage());
        }
        evidence.save(item);
        audit.record(authentication, "GATE_EVIDENCE_HOLD_APPROVED", "PARKING_SESSION",
            parkingSessionId.toString(), "SUCCESS", operation.name() + ":preserveUntil=" + normalizedDeadline
                + ";reason=" + item.getPreserveReason(), item.getId());
    }

    @Transactional
    public void releasePreservation(Long parkingSessionId, GateOperationType operation, String evidenceId,
            Authentication authentication) {
        if (!isManagement(authentication)) throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        GateEvidence item = requireSessionEvidence(parkingSessionId, operation, evidenceId);
        item.releasePreservation();
        evidence.save(item);
        audit.record(authentication, "GATE_EVIDENCE_HOLD_RELEASED", "PARKING_SESSION",
            parkingSessionId.toString(), "SUCCESS", operation.name() + ":MANAGEMENT_RELEASE", item.getId());
    }

    private GateEvidence requireSessionEvidence(Long parkingSessionId, GateOperationType operation, String evidenceId) {
        if (parkingSessionId == null || operation == null || !sessions.existsById(parkingSessionId))
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Gate evidence is unavailable");
        GateEvidence item = evidence.findById(evidenceId).orElse(null);
        if (item == null || !java.util.Objects.equals(item.getParkingSessionId(), parkingSessionId)
                || item.getOperationType() != operation)
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Gate evidence is unavailable");
        return item;
    }

    private GateEvidence ownedByCurrentSession(String id, Authentication authentication) {
        Actor actor = actor(authentication);
        return evidence.findByIdAndOwnerAccountIdAndDesktopSessionId(id, actor.accountId(), actor.sessionId())
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Gate evidence is unavailable"));
    }

    private GateEvidence lockOwnedByCurrentSession(String id, Authentication authentication) {
        if (id == null || !EVIDENCE_ID.matcher(id).matches())
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Valid gate evidence is required");
        Actor actor = actor(authentication);
        return evidence.lockByIdAndOwnerAccountIdAndDesktopSessionId(id, actor.accountId(), actor.sessionId())
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Gate evidence is unavailable"));
    }

    private String storeFaceEvidence(byte[] image, String plate, String decision, Double similarity,
            Double threshold, Long familyMemberId, GateOperationType operation, Long parkingSessionId,
            Authentication authentication) {
        Actor actor = actor(authentication);
        String normalizedPlate = PlateNormalizer.normalize(plate);
        if (operation == null || normalizedPlate.isBlank() || !isDecodableJpeg(image))
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "ANPR service returned an invalid face image");
        if (familyMemberId != null && familyMemberId < 1)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid family member");
        Instant capturedAt = Instant.now();
        String filename;
        try {
            filename = storage.store(image, MAX_IMAGE_BYTES);
        } catch (IOException ex) {
            throw new ResponseStatusException(HttpStatus.INSUFFICIENT_STORAGE, "Unable to store face evidence");
        }
        String id = UUID.randomUUID().toString();
        GateEvidence item = new GateEvidence(id, actor.accountId(), actor.sessionId(), operation,
            GateEvidenceKind.FACE_VERIFICATION, filename, "image/jpeg", image.length, sha256(image), capturedAt,
            policy.expiresAt(capturedAt), capturedAt);
        item.setFaceVerification(normalizedPlate, decision, similarity, threshold, familyMemberId, null, "fastapi-face");
        item.setRecognitionValidUntil(capturedAt.plus(RECOGNITION_USE_WINDOW));
        if (parkingSessionId != null) item.bindToParkingSession(parkingSessionId);
        evidence.saveAndFlush(item);
        audit.record(authentication, "GATE_EVIDENCE_CAPTURED", "GATE_EVIDENCE", id,
            "SUCCESS", "FACE_" + decision, id);
        return id;
    }

    private static void validateFaceEvidence(GateEvidence item, GateOperationType operation, String plate,
            String decision, Instant now) {
        if (item.getEvidenceKind() != GateEvidenceKind.FACE_VERIFICATION || item.getOperationType() != operation
                || !decision.equals(item.getVerificationDecision())
                || !PlateNormalizer.normalize(item.getRecognizedPlate()).equals(PlateNormalizer.normalize(plate)))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Face evidence does not match this operation");
        if (item.getRecognitionValidUntil() == null || !item.getRecognitionValidUntil().isAfter(now)
                || !item.getExpiresAt().isAfter(now))
            throw new ResponseStatusException(HttpStatus.GONE, "Face evidence has expired");
    }

    private void validateLinkedFaceEvidence(String recognitionEvidenceId, GateEvidence faceEvidence,
            GateOperationType operation, String plate, Authentication authentication) {
        GateEvidence recognition = lockOwnedByCurrentSession(recognitionEvidenceId, authentication);
        if ("UNAVAILABLE".equals(recognition.getRecognitionStatus()))
            validateUnavailableEvidence(recognition, operation, Instant.now(), faceEvidence.getParkingSessionId());
        else
            validateRecognition(recognition, operation, plate, Instant.now());
        if (!java.util.Objects.equals(recognition.getFaceVerificationEvidenceId(), faceEvidence.getId())
                || !java.util.Objects.equals(recognition.getFaceVerificationStatus(),
                    faceEvidence.getVerificationDecision()))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Face evidence is not linked to this recognition");
    }

    private static void requireOverrideAuthority(Authentication authentication) {
        if (!hasAuthority(authentication, "OVERRIDE_CREATE"))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Override permission is required");
    }

    private static void validateRecognition(GateEvidence item, GateOperationType operation, String plate, Instant now) {
        if (item.getOperationType() != operation || item.getEvidenceKind() != GateEvidenceKind.AI_RECOGNITION
                || !"SUCCESS".equals(item.getRecognitionStatus()))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Gate evidence is for a different operation");
        if (item.getRecognitionValidUntil() == null || !item.getRecognitionValidUntil().isAfter(now)
                || !item.getExpiresAt().isAfter(now) || item.getConsumedAt() != null)
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Gate recognition is expired or already used");
        String recognizedPlate = PlateNormalizer.normalize(item.getRecognizedPlate());
        String suppliedPlate = PlateNormalizer.normalize(plate);
        if (recognizedPlate.isBlank() || suppliedPlate.isBlank() || !recognizedPlate.equals(suppliedPlate))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Plate does not match server recognition");
    }

    private static void validateUnavailableEvidence(GateEvidence item, GateOperationType operation, Instant now,
            Long parkingSessionId) {
        if (item.getOperationType() != operation || item.getEvidenceKind() != GateEvidenceKind.AI_RECOGNITION
                || !"UNAVAILABLE".equals(item.getRecognitionStatus())
                || item.getRecognitionFailureAt() == null
                || !item.getCapturedAt().plus(RECOGNITION_USE_WINDOW).isAfter(now)
                || !item.getExpiresAt().isAfter(now) || item.getConsumedAt() != null
                || (item.getParkingSessionId() != null
                    && !java.util.Objects.equals(item.getParkingSessionId(), parkingSessionId)))
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                "Unavailable gate evidence is expired, used, or belongs to another operation");
    }

    private static void validateEvidenceForBinding(GateEvidence item, GateOperationType operation, String plate,
            Long parkingSessionId, Instant now) {
        if ("UNAVAILABLE".equals(item.getRecognitionStatus()))
            validateUnavailableEvidence(item, operation, now, parkingSessionId);
        else
            validateRecognition(item, operation, plate, now);
    }

    private static boolean canRead(GateEvidence item, Actor actor, Authentication authentication,
            ParkingSession session) {
        if (isManagement(authentication)) return true;
        return hasAuthority(authentication, "ROLE_GATE_STAFF")
            && session.getStatus() == SessionStatus.OPEN
            && java.util.Objects.equals(item.getOwnerAccountId(), actor.accountId())
            && java.util.Objects.equals(item.getDesktopSessionId(), actor.sessionId());
    }

    private void auditEvidenceRead(Authentication authentication, Long parkingSessionId,
            GateOperationType operation, String evidenceId, boolean derived, String outcome) {
        String action = derived ? "DERIVED_IMAGE" : "ORIGINAL_IMAGE";
        String safeId = safeReference(evidenceId);
        audit.record(authentication, "GATE_EVIDENCE_READ", "PARKING_SESSION",
            parkingSessionId == null ? null : parkingSessionId.toString(), outcome,
            (operation == null ? "UNKNOWN" : operation.name()) + ":" + action, safeId);
    }

    private static Actor actor(Authentication authentication) {
        if (!(authentication instanceof JwtAuthenticationToken jwt))
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        try {
            long accountId = Long.parseLong(jwt.getToken().getSubject());
            String sessionId = jwt.getToken().getClaimAsString("sid");
            if (accountId < 1 || sessionId == null || sessionId.isBlank()) throw new NumberFormatException();
            return new Actor(accountId, sessionId);
        } catch (RuntimeException ex) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        }
    }

    private static boolean isManagement(Authentication authentication) {
        return hasAuthority(authentication, "ROLE_MANAGEMENT");
    }

    private static boolean hasAuthority(Authentication authentication, String required) {
        return authentication != null && authentication.getAuthorities().stream()
            .map(GrantedAuthority::getAuthority).anyMatch(required::equals);
    }

    private static byte[] decodeAnnotatedImage(String value) {
        try {
            byte[] decoded = Base64.getDecoder().decode(value);
            if (decoded.length == 0 || decoded.length > MAX_IMAGE_BYTES)
                throw new IllegalArgumentException("Invalid annotated image");
            return decoded;
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "ANPR service returned an invalid image");
        }
    }

    private static boolean isDecodableJpeg(byte[] bytes) {
        if (bytes == null || bytes.length == 0 || bytes.length > MAX_IMAGE_BYTES
                || bytes.length < 3 || (bytes[0] & 0xff) != 0xff || (bytes[1] & 0xff) != 0xd8
                || (bytes[2] & 0xff) != 0xff) return false;
        try {
            return ImageIO.read(new ByteArrayInputStream(bytes)) != null;
        } catch (IOException ex) {
            return false;
        }
    }

    private static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is unavailable", ex);
        }
    }

    private static String safeReference(String id) {
        return id != null && EVIDENCE_ID.matcher(id).matches() ? id : null;
    }

    public record EvidenceImage(byte[] bytes, String mediaType) { }
    public record Recognition(String plateNumber, String vehicleType, boolean recognitionUnavailable,
            String faceVerificationStatus, String faceEvidenceId, String evidenceId) { }
    public record FaceProof(String evidenceId, String decision, Double similarity, Long familyMemberId) { }
    private record Actor(long accountId, String sessionId) { }
}

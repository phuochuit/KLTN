package vn.edu.parking.service;

import java.time.Instant;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Service;
import vn.edu.parking.domain.*;
import vn.edu.parking.repository.GateEvidenceRepository;
import vn.edu.parking.repository.SystemAccountRepository;

@Service
public class GateEvidenceConsumptionService {
    private final GateEvidenceRepository evidences;
    private final SystemAccountRepository accounts;
    private final boolean required;

    public GateEvidenceConsumptionService(GateEvidenceRepository evidences,
            SystemAccountRepository accounts,
            @Value("${parking.evidence.required:false}") boolean required) {
        this.evidences = evidences;
        this.accounts = accounts;
        this.required = required;
    }

    public ResolvedEvidence resolveForConsumption(String vehicleId, String faceId,
            EvidenceOperation operation, Long familyMemberId) {
        return resolve(vehicleId, faceId, operation, familyMemberId, true);
    }

    public ResolvedEvidence resolveForPreview(String vehicleId, String faceId,
            EvidenceOperation operation, Long familyMemberId) {
        return resolve(vehicleId, faceId, operation, familyMemberId, false);
    }

    public void consume(ResolvedEvidence resolved, ParkingSession parkingSession) {
        if (resolved.legacy()) return;
        Instant now = Instant.now();
        SystemAccount account = accounts.findById(resolved.accountId())
            .orElseThrow(() -> new IllegalStateException("Không tìm thấy tài khoản consume evidence"));
        resolved.vehicleEvidence().consume(parkingSession, account, now);
        if (resolved.faceEvidence() != null)
            resolved.faceEvidence().consume(parkingSession, account, now);
    }

    private ResolvedEvidence resolve(String vehicleId, String faceId, EvidenceOperation operation,
            Long familyMemberId, boolean lock) {
        boolean noVehicle = vehicleId == null || vehicleId.isBlank();
        boolean noFace = faceId == null || faceId.isBlank();
        if (noVehicle && noFace) {
            if (required) throw new IllegalStateException("Thao tác yêu cầu Gate Evidence");
            return ResolvedEvidence.legacyEvidence();
        }
        if (noVehicle) throw new IllegalStateException("Thiếu evidence nhận dạng xe");
        if (operation == null) throw new IllegalStateException("Thiếu chiều nghiệp vụ evidence");
        Identity identity = identity();
        GateEvidence vehicle = load(vehicleId, lock);
        validate(vehicle, EvidenceType.VEHICLE, operation, identity, null);
        GateEvidence face = null;
        if (!noFace) {
            if (vehicleId.equals(faceId)) throw new IllegalStateException("Evidence xe và khuôn mặt không được trùng nhau");
            face = load(faceId, lock);
            validate(face, EvidenceType.FACE, operation, identity, familyMemberId);
        }
        boolean facePassed = face != null && "PASS".equals(face.getFaceDecision());
        Double similarity = face == null || face.getFaceSimilarity() == null
            ? null : face.getFaceSimilarity().doubleValue();
        return new ResolvedEvidence(false, identity.accountId(), vehicle, face,
            vehicle.getPlateNumber(), vehicle.getVehicleType(), facePassed, similarity,
            face == null || face.getFamilyMember() == null ? null : face.getFamilyMember().getId());
    }

    private GateEvidence load(String id, boolean lock) {
        try { java.util.UUID.fromString(id); }
        catch (RuntimeException ex) { throw new IllegalStateException("Evidence ID không hợp lệ", ex); }
        return (lock ? evidences.lockById(id) : evidences.findById(id))
            .orElseThrow(() -> new IllegalStateException("Không tìm thấy Gate Evidence"));
    }

    private static void validate(GateEvidence evidence, EvidenceType type,
            EvidenceOperation operation, Identity identity, Long expectedMemberId) {
        if (evidence.getStatus() != EvidenceStatus.READY)
            throw new IllegalStateException("Evidence đã được sử dụng hoặc không sẵn sàng");
        if (!Instant.now().isBefore(evidence.getExpiresAt()))
            throw new IllegalStateException("Evidence đã hết hạn");
        if (evidence.getEvidenceType() != type || evidence.getOperation() != operation)
            throw new IllegalStateException("Evidence không đúng loại hoặc chiều nghiệp vụ");
        if (evidence.getAccount().getId() == null || !evidence.getAccount().getId().equals(identity.accountId()))
            throw new IllegalStateException("Evidence không thuộc tài khoản hiện tại");
        if (!evidence.getDesktopSession().getSessionId().equals(identity.sessionId()))
            throw new IllegalStateException("Evidence không thuộc Desktop session hiện tại");
        Long actualMemberId = evidence.getFamilyMember() == null ? null : evidence.getFamilyMember().getId();
        if (expectedMemberId != null && !expectedMemberId.equals(actualMemberId))
            throw new IllegalStateException("Evidence khuôn mặt không khớp thành viên được chọn");
    }

    private static Identity identity() {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (!(authentication instanceof JwtAuthenticationToken jwt))
            throw new IllegalStateException("Gate Evidence yêu cầu Desktop JWT");
        try {
            Long accountId = Long.valueOf(jwt.getToken().getSubject());
            String sessionId = jwt.getToken().getClaimAsString("sid");
            if (sessionId == null || sessionId.isBlank()) throw new NumberFormatException("missing sid");
            return new Identity(accountId, sessionId);
        } catch (RuntimeException ex) {
            throw new IllegalStateException("Desktop JWT thiếu identity cho evidence", ex);
        }
    }

    private record Identity(Long accountId, String sessionId) { }
    public record ResolvedEvidence(boolean legacy, Long accountId,
            GateEvidence vehicleEvidence, GateEvidence faceEvidence,
            String plateNumber, String vehicleType, boolean faceVerified,
            Double faceSimilarity, Long familyMemberId) {
        static ResolvedEvidence legacyEvidence() {
            return new ResolvedEvidence(true, null, null, null, null, null, false, null, null);
        }
    }
}

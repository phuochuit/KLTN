package vn.edu.parking.service;

import java.time.Instant;
import java.util.Locale;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Service;
import vn.edu.parking.domain.*;
import vn.edu.parking.repository.*;
import vn.edu.parking.service.GateEvidenceConsumptionService.ResolvedEvidence;

@Service
public class ManualOverrideService {
    private final SystemAccountRepository accounts;
    private final DesktopSessionRepository desktopSessions;
    private final GateShiftAssignmentRepository shifts;
    private final ManualOverrideEventRepository events;
    private final ManualOverrideEventRecorder rejectedEvents;
    private final SecurityAuditService audit;

    public ManualOverrideService(SystemAccountRepository accounts,
            DesktopSessionRepository desktopSessions, GateShiftAssignmentRepository shifts,
            ManualOverrideEventRepository events, ManualOverrideEventRecorder rejectedEvents,
            SecurityAuditService audit) {
        this.accounts = accounts;
        this.desktopSessions = desktopSessions;
        this.shifts = shifts;
        this.events = events;
        this.rejectedEvents = rejectedEvents;
        this.audit = audit;
    }

    public Authorization authorize(boolean requested, EvidenceOperation operation,
            String reasonCodeValue, String reasonTextValue, ResolvedEvidence evidence) {
        if (!requested) return Authorization.notRequested();
        Identity identity = identity();
        SystemAccount account = accounts.findById(identity.accountId())
            .orElseThrow(() -> new IllegalStateException("Không tìm thấy tài khoản đang thực hiện xử lý thủ công"));
        DesktopSession desktop = desktopSessions.findById(identity.sessionId())
            .orElseThrow(() -> new IllegalStateException("Phiên đăng nhập không còn hợp lệ. Vui lòng đăng nhập lại"));
        Instant now = Instant.now();
        GateShiftAssignment shift = shifts
            .findByAccount_IdAndStartsAtLessThanEqualAndEndsAtGreaterThanOrderByStartsAtDesc(
                account.getId(), now, now).stream().findFirst().orElse(null);
        String text = reasonTextValue == null ? "" : reasonTextValue.trim();
        ManualOverrideReasonCode code = parseReason(reasonCodeValue);
        String rejection = validate(account, desktop, shift, code, text, evidence, now);
        if (rejection != null) {
            rejectedEvents.rejected(operation, reasonCodeValue, text, account.getId(),
                desktop.getSessionId(), shift == null ? null : shift.getId(),
                id(evidence.vehicleEvidence()), id(evidence.faceEvidence()), rejection);
            throw new IllegalStateException(rejection);
        }
        return new Authorization(true, operation, code, text, account, desktop, shift,
            evidence.vehicleEvidence(), evidence.faceEvidence());
    }

    public void recordApproved(Authorization authorization, ParkingSession parkingSession) {
        recordApproved(authorization, parkingSession, null, null, null, null);
    }

    public void recordApproved(Authorization authorization, ParkingSession parkingSession,
            String originalPlate, String correctedPlate, String originalVehicleType,
            String correctedVehicleType) {
        if (!authorization.requested()) return;
        var event = new ManualOverrideEvent(authorization.operation(), ManualOverrideOutcome.APPROVED,
            authorization.reasonCode().name(), authorization.reasonText(), authorization.account(),
            authorization.desktopSession(), authorization.shift(), parkingSession,
            authorization.vehicleEvidence(), authorization.faceEvidence(), originalPlate, correctedPlate,
            originalVehicleType, correctedVehicleType, Instant.now());
        event = events.save(event);
        audit.recordCurrent("MANUAL_OVERRIDE_APPROVED", "MANUAL_OVERRIDE_EVENT",
            event.getId() == null ? null : event.getId().toString(), "SUCCESS", null,
            id(authorization.faceEvidence()) != null
                ? id(authorization.faceEvidence()) : id(authorization.vehicleEvidence()));
    }

    private static String validate(SystemAccount account, DesktopSession desktop,
            GateShiftAssignment shift, ManualOverrideReasonCode code, String text,
            ResolvedEvidence resolved, Instant now) {
        if (!account.isEnabled() || !canCorrectAiResult(account))
            return "Chỉ nhân viên trạm gác đang hoạt động mới có thể xử lý thủ công";
        if (!desktop.getAccount().getId().equals(account.getId()) || !desktop.isActiveAt(now))
            return "Phiên đăng nhập không còn hợp lệ. Vui lòng đăng nhập lại";
        if (shift == null) return "Bạn cần có ca trực đang hoạt động trước khi xử lý thủ công";
        if (code == null) return "Vui lòng chọn lý do xử lý phù hợp";
        if (text.length() < 10 || text.length() > 500)
            return "Nội dung mô tả phải từ 10 đến 500 ký tự";
        if (resolved == null || resolved.legacy() || resolved.vehicleEvidence() == null
                || resolved.faceEvidence() == null)
            return "Cần có đầy đủ kết quả nhận diện xe và khuôn mặt trước khi xử lý thủ công";
        String decision = resolved.faceEvidence().getFaceDecision();
        boolean matches = switch (code) {
            case PLATE_NOT_DETECTED -> resolved.vehicleEvidence().getPlateNumber() == null
                || resolved.vehicleEvidence().getPlateNumber().isBlank();
            case PLATE_CORRECTION, VEHICLE_TYPE_CORRECTION -> true;
            case FACE_REVIEW -> "REVIEW".equals(decision);
            case FACE_FAILED -> "FAIL".equals(decision);
            case REGISTRATION_IMAGE_MISSING -> "CAPTURED".equals(decision);
        };
        return matches ? null : "Lý do đã chọn không phù hợp với kết quả nhận diện khuôn mặt";
    }

    private static boolean canCorrectAiResult(SystemAccount account) {
        if (account.getSecurityRole() == null)
            return account.getRole() == AccountRole.GATE_STAFF;
        return account.getSecurityRole().isEnabled()
            && account.getSecurityRole().getPermissions().contains(SecurityPermission.AI_RESULT_CORRECT);
    }

    private static ManualOverrideReasonCode parseReason(String value) {
        try {
            return value == null ? null : ManualOverrideReasonCode.valueOf(
                value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) { return null; }
    }

    private static Identity identity() {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (!(authentication instanceof JwtAuthenticationToken jwt))
            throw new IllegalStateException("Vui lòng thực hiện thao tác này từ ứng dụng trạm gác sau khi đăng nhập");
        try {
            return new Identity(Long.valueOf(jwt.getToken().getSubject()),
                jwt.getToken().getClaimAsString("sid"));
        } catch (RuntimeException ex) {
            throw new IllegalStateException("Phiên đăng nhập thiếu thông tin cần thiết. Vui lòng đăng nhập lại", ex);
        }
    }

    private static String id(GateEvidence evidence) { return evidence == null ? null : evidence.getId(); }
    private record Identity(Long accountId, String sessionId) { }
    public record Authorization(boolean requested, EvidenceOperation operation,
            ManualOverrideReasonCode reasonCode, String reasonText, SystemAccount account,
            DesktopSession desktopSession, GateShiftAssignment shift,
            GateEvidence vehicleEvidence, GateEvidence faceEvidence) {
        static Authorization notRequested() {
            return new Authorization(false, null, null, null, null, null, null, null, null);
        }
    }
}

package vn.edu.parking.service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.edu.parking.domain.*;
import vn.edu.parking.repository.*;
import vn.edu.parking.web.dto.*;

@Service
@Transactional(readOnly = true)
public class ExceptionReportService {
    private final ManualOverrideEventRepository manualOverrides;
    private final SecurityAuditRepository securityAudits;
    private final PaymentTransactionRepository paymentTransactions;
    private final GateEvidenceRepository gateEvidences;

    public ExceptionReportService(
            ManualOverrideEventRepository manualOverrides,
            SecurityAuditRepository securityAudits,
            PaymentTransactionRepository paymentTransactions,
            GateEvidenceRepository gateEvidences) {
        this.manualOverrides = manualOverrides;
        this.securityAudits = securityAudits;
        this.paymentTransactions = paymentTransactions;
        this.gateEvidences = gateEvidences;
    }

    public ExceptionReportPageResponse getExceptionReport(
            Instant from,
            Instant to,
            String categoryFilter,
            String severityFilter,
            String operatorFilter,
            String keyword) {

        Instant effectiveTo = to != null ? to : Instant.now();
        Instant effectiveFrom = from != null ? from : effectiveTo.minus(7, ChronoUnit.DAYS);

        List<ExceptionReportItemResponse> allItems = new ArrayList<>();

        // 1. Manual Overrides
        List<ManualOverrideEvent> overrides = manualOverrides.findTop500ByCreatedAtBetweenOrderByCreatedAtDesc(effectiveFrom, effectiveTo);
        for (ManualOverrideEvent ev : overrides) {
            Map<String, String> details = new LinkedHashMap<>();
            if (ev.getOriginalPlateNumber() != null) details.put("Biển số ban đầu", ev.getOriginalPlateNumber());
            if (ev.getCorrectedPlateNumber() != null) details.put("Biển số đã sửa", ev.getCorrectedPlateNumber());
            if (ev.getOriginalVehicleType() != null) details.put("Loại xe gốc", translateVehicleType(ev.getOriginalVehicleType()));
            if (ev.getCorrectedVehicleType() != null) details.put("Loại xe đã sửa", translateVehicleType(ev.getCorrectedVehicleType()));

            String operatorUser = ev.getAccount() != null ? ev.getAccount().getUsername() : "UNKNOWN";
            String operatorName = ev.getAccount() != null && ev.getAccount().getFullName() != null
                ? ev.getAccount().getFullName() : operatorUser;
            String plate = ev.getCorrectedPlateNumber() != null && !ev.getCorrectedPlateNumber().isBlank()
                ? ev.getCorrectedPlateNumber() : ev.getOriginalPlateNumber();

            allItems.add(new ExceptionReportItemResponse(
                "OVR-" + ev.getId(),
                ev.getCreatedAt(),
                "MANUAL_OVERRIDE",
                "Can thiệp thủ công",
                "WARNING",
                plate != null ? plate : "-",
                operatorUser,
                operatorName,
                null,
                ev.getOperation() != null ? ev.getOperation().name() : "GATE",
                translateReasonCode(ev.getReasonCode()),
                ev.getReasonText(),
                translateOutcome(ev.getOutcome() != null ? ev.getOutcome().name() : "APPROVED"),
                null,
                details
            ));
        }

        // 2. Security Audit Warnings/Failures
        List<SecurityAuditEvent> audits = securityAudits.findTop500ByOccurredAtBetweenOrderByOccurredAtDesc(effectiveFrom, effectiveTo);
        for (SecurityAuditEvent sa : audits) {
            boolean isWarningOrFailure = "FAILED".equalsIgnoreCase(sa.getOutcome())
                || "NO_MATCH".equalsIgnoreCase(sa.getOutcome())
                || "REVIEW".equalsIgnoreCase(sa.getOutcome())
                || sa.getAction().contains("FAILURE")
                || sa.getAction().contains("TAMPER")
                || sa.getAction().contains("OVERRIDE");

            if (isWarningOrFailure) {
                Map<String, String> details = new LinkedHashMap<>();
                if (sa.getTargetType() != null) details.put("Đối tượng", translateTargetType(sa.getTargetType()));
                if (sa.getTargetReference() != null) details.put("Mã tham chiếu", sa.getTargetReference());
                if (sa.getEvidenceReference() != null) details.put("Bằng chứng", sa.getEvidenceReference());

                String sev = "FAILED".equalsIgnoreCase(sa.getOutcome()) ? "CRITICAL" : "WARNING";
                String actorUser = sa.getActorUsername() != null ? sa.getActorUsername() : "SYSTEM";
                String actorName = sa.getActorUsername() != null ? sa.getActorUsername() : "Hệ thống";
                allItems.add(new ExceptionReportItemResponse(
                    "AUD-" + sa.getId(),
                    sa.getOccurredAt(),
                    "SECURITY_ALERT",
                    "Cảnh báo an ninh",
                    sev,
                    sa.getTargetReference() != null ? sa.getTargetReference() : "-",
                    actorUser,
                    actorName,
                    null,
                    "AUDIT",
                    translateAuditAction(sa.getAction()),
                    translateAuditReason(sa.getReason(), sa.getAction()),
                    translateOutcome(sa.getOutcome()),
                    null,
                    details
                ));
            }
        }

        // 3. Payment Issues (Voided, Refunded or Reconciled with notes)
        List<PaymentTransaction> payments = paymentTransactions.findByPaidAtBetweenOrderByPaidAtDesc(effectiveFrom, effectiveTo);
        for (PaymentTransaction pt : payments) {
            boolean isIssue = pt.getStatus() == PaymentStatus.VOIDED
                || pt.getStatus() == PaymentStatus.REFUNDED
                || (pt.getReconciliationNote() != null && !pt.getReconciliationNote().isBlank());

            if (isIssue) {
                Map<String, String> details = new LinkedHashMap<>();
                details.put("Biên nhận", pt.getReceiptNumber());
                details.put("Số tiền", String.format("%,.0f đ", pt.getAmount()));
                details.put("Hình thức", translatePaymentMethod(pt.getPaymentMethod().name()));
                if (pt.getReconciliationNote() != null) details.put("Ghi chú đối soát", pt.getReconciliationNote());

                String plate = pt.getParkingSession() != null ? pt.getParkingSession().getEntryPlate() : "-";
                String collector = pt.getCollectedBy() != null ? pt.getCollectedBy().getUsername() : "SYSTEM";
                String collectorName = pt.getCollectedBy() != null ? pt.getCollectedBy().getFullName() : collector;

                boolean isCritical = pt.getStatus() == PaymentStatus.VOIDED || pt.getStatus() == PaymentStatus.REFUNDED;

                allItems.add(new ExceptionReportItemResponse(
                    "PAY-" + pt.getId(),
                    pt.getPaidAt(),
                    "PAYMENT_ISSUE",
                    "Lỗi thanh toán và đối soát",
                    isCritical ? "CRITICAL" : "WARNING",
                    plate,
                    collector,
                    collectorName,
                    null,
                    "PAYMENT",
                    pt.getStatus().getDisplayName(),
                    pt.getReconciliationNote() != null ? pt.getReconciliationNote() : ("Giao dịch thanh toán: " + pt.getStatus().getDisplayName()),
                    translateOutcome(pt.getStatus().name()),
                    pt.getParkingSession() != null ? pt.getParkingSession().getId() : null,
                    details
                ));
            }
        }

        // 4. Face Recognition Rejections / Reviews
        List<GateEvidence> faceEvidences = gateEvidences.findRecentFaceExceptions(
            EvidenceType.FACE, List.of("REJECT", "REVIEW"), effectiveFrom, effectiveTo);
        for (GateEvidence fe : faceEvidences) {
            Map<String, String> details = new LinkedHashMap<>();
            if (fe.getFaceDecision() != null) details.put("Kết quả đối chiếu", translateFaceDecision(fe.getFaceDecision()));

            String opUser = fe.getAccount() != null ? fe.getAccount().getUsername() : "UNKNOWN";
            String opName = fe.getAccount() != null ? fe.getAccount().getFullName() : opUser;

            allItems.add(new ExceptionReportItemResponse(
                "FAC-" + fe.getId().substring(0, Math.min(fe.getId().length(), 12)),
                fe.getCreatedAt(),
                "FACE_REJECT",
                "Xác thực khuôn mặt chưa đạt",
                "REJECT".equalsIgnoreCase(fe.getFaceDecision()) ? "CRITICAL" : "WARNING",
                fe.getPlateNumber() != null ? fe.getPlateNumber() : "-",
                opUser,
                opName,
                null,
                fe.getOperation() != null ? fe.getOperation().name() : "GATE",
                translateFaceDecision(fe.getFaceDecision()),
                "Khuôn mặt " + ("REJECT".equalsIgnoreCase(fe.getFaceDecision())
                    ? "chưa khớp với ảnh đăng ký" : "cần được xem lại")
                    + ". Vui lòng đối chiếu thủ công trước khi xác nhận.",
                translateFaceDecision(fe.getFaceDecision()),
                fe.getParkingSession() != null ? fe.getParkingSession().getId() : null,
                details
            ));
        }

        // 5. AI Quality Warnings (blur, glare, low light, tilt, small crop)
        List<GateEvidence> qualityEvidences = gateEvidences.findRecentQualityWarnings(effectiveFrom, effectiveTo);
        for (GateEvidence qe : qualityEvidences) {
            String status = qe.getQualityStatus() != null ? qe.getQualityStatus() : "WARNING";
            List<String> codes = Arrays.stream(qe.getQualityWarnings().split(","))
                .map(String::trim)
                .filter(code -> !code.isBlank())
                .toList();
            if (codes.isEmpty()) continue;

            String readable = String.join("; ",
                codes.stream().map(ExceptionReportService::translateQualityWarning).toList());
            Map<String, String> details = new LinkedHashMap<>();
            details.put("Mức chất lượng", translateQualityStatus(status));
            details.put("Diễn giải", readable);
            if (qe.getLivenessStatus() != null) details.put("Xác thực khuôn mặt", translateLiveness(qe.getLivenessStatus().name()));

            String opUser = qe.getAccount() != null ? qe.getAccount().getUsername() : "UNKNOWN";
            String opName = qe.getAccount() != null && qe.getAccount().getFullName() != null
                ? qe.getAccount().getFullName() : opUser;

            allItems.add(new ExceptionReportItemResponse(
                "QUA-" + qe.getId().substring(0, Math.min(qe.getId().length(), 12)),
                qe.getCreatedAt(),
                "AI_QUALITY",
                "Cảnh báo chất lượng ảnh",
                "REJECT".equalsIgnoreCase(status) ? "CRITICAL" : "WARNING",
                qe.getPlateNumber() != null ? qe.getPlateNumber() : "-",
                opUser,
                opName,
                null,
                qe.getOperation() != null ? qe.getOperation().name() : "GATE",
                status,
                "Cảnh báo chất lượng ảnh: " + readable,
                translateQualityStatus(status),
                qe.getParkingSession() != null ? qe.getParkingSession().getId() : null,
                details
            ));
        }

        // Compute overall summary statistics BEFORE filtering
        long totalExceptions = allItems.size();
        long manualOverridesCount = allItems.stream().filter(i -> "MANUAL_OVERRIDE".equals(i.category())).count();
        long securityAlertsCount = allItems.stream().filter(i -> "SECURITY_ALERT".equals(i.category())).count();
        long paymentIssuesCount = allItems.stream().filter(i -> "PAYMENT_ISSUE".equals(i.category())).count();
        long faceRejectsCount = allItems.stream().filter(i -> "FACE_REJECT".equals(i.category())).count();
        long qualityWarningsCount = allItems.stream().filter(i -> "AI_QUALITY".equals(i.category())).count();
        ExceptionReportSummary summary = new ExceptionReportSummary(
            totalExceptions, manualOverridesCount, securityAlertsCount, paymentIssuesCount,
            faceRejectsCount, qualityWarningsCount);

        // Apply filters
        var filteredStream = allItems.stream();
        if (categoryFilter != null && !categoryFilter.isBlank() && !"ALL".equalsIgnoreCase(categoryFilter)) {
            filteredStream = filteredStream.filter(i -> i.category().equalsIgnoreCase(categoryFilter));
        }
        if (severityFilter != null && !severityFilter.isBlank() && !"ALL".equalsIgnoreCase(severityFilter)) {
            filteredStream = filteredStream.filter(i -> i.severity().equalsIgnoreCase(severityFilter));
        }
        if (operatorFilter != null && !operatorFilter.isBlank()) {
            String op = operatorFilter.trim().toLowerCase();
            filteredStream = filteredStream.filter(i ->
                i.operatorUsername().toLowerCase().contains(op) ||
                (i.operatorFullName() != null && i.operatorFullName().toLowerCase().contains(op)));
        }
        if (keyword != null && !keyword.isBlank()) {
            String kw = keyword.trim().toLowerCase();
            filteredStream = filteredStream.filter(i ->
                i.plateNumber().toLowerCase().contains(kw) ||
                i.reasonCode().toLowerCase().contains(kw) ||
                i.description().toLowerCase().contains(kw) ||
                i.id().toLowerCase().contains(kw));
        }

        List<ExceptionReportItemResponse> filteredList = filteredStream
            .sorted(Comparator.comparing(ExceptionReportItemResponse::timestamp).reversed())
            .toList();

        return new ExceptionReportPageResponse(filteredList, summary, filteredList.size());
    }

    private static String translateOutcome(String outcome) {
        if (outcome == null || outcome.isBlank()) return "Đã ghi nhận";
        if (!isTechnicalCode(outcome)) return outcome;
        return switch (outcome) {
            case "APPROVED" -> "Đã chấp nhận";
            case "REJECTED" -> "Đã từ chối";
            case "SUCCESS", "PASSED", "PASS" -> "Đạt";
            case "FAILED", "FAILURE", "FAIL", "DENIED", "REJECT" -> "Chưa đạt";
            case "NO_MATCH", "MISMATCH" -> "Không khớp";
            case "REVIEW", "PENDING" -> "Cần xem lại";
            case "PAID" -> "Đã thanh toán";
            case "VOIDED", "CANCELLED" -> "Đã hủy";
            case "REFUNDED" -> "Đã hoàn trả";
            case "RECONCILED" -> "Đã đối soát";
            case "WARNING" -> "Cảnh báo";
            case "MATCH" -> "Khớp";
            default -> "Đã ghi nhận";
        };
    }

    private static boolean isTechnicalCode(String value) {
        return value != null && value.matches("[A-Z][A-Z0-9_]*");
    }

    private static String translateAuditAction(String action) {
        if (action == null || action.isBlank()) return "Hoạt động cần kiểm tra";
        return switch (action) {
            case "LOGIN_FAILURE", "LOGIN_FAILED" -> "Đăng nhập không thành công";
            case "LOGIN_SUCCESS" -> "Đăng nhập thành công";
            case "LOGOUT" -> "Đăng xuất";
            case "TAMPER_DETECTED" -> "Phát hiện can thiệp thiết bị";
            case "ACCESS_DENIED", "PERMISSION_DENIED" -> "Truy cập bị từ chối";
            case "PASSWORD_CHANGED" -> "Đổi mật khẩu";
            case "PASSWORD_RESET" -> "Đặt lại mật khẩu";
            case "SESSION_REVOKED" -> "Thu hồi phiên đăng nhập";
            case "OVERRIDE_APPROVED" -> "Chấp thuận can thiệp thủ công";
            case "OVERRIDE_REJECTED" -> "Từ chối can thiệp thủ công";
            case "ROLE_CHANGED" -> "Thay đổi vai trò";
            case "ACCOUNT_LOCKED" -> "Khóa tài khoản";
            case "ACCOUNT_UNLOCKED" -> "Mở khóa tài khoản";
            case "ACCOUNT_DISABLED" -> "Vô hiệu hóa tài khoản";
            default -> isTechnicalCode(action) ? "Hoạt động cần kiểm tra" : action;
        };
    }

    private static String translateAuditReason(String reason, String action) {
        if (reason == null || reason.isBlank()) return "Hệ thống ghi nhận hoạt động này cần được kiểm tra.";
        if (!isTechnicalCode(reason)) return reason;
        return switch (reason) {
            case "INVALID_CREDENTIALS" -> "Sai tên đăng nhập hoặc mật khẩu.";
            case "ACCOUNT_LOCKED" -> "Tài khoản đang tạm khóa.";
            case "ACCOUNT_DISABLED" -> "Tài khoản đã bị vô hiệu hóa.";
            case "SESSION_EXPIRED" -> "Phiên đăng nhập đã hết hạn.";
            case "INSUFFICIENT_PRIVILEGES", "ACCESS_DENIED" -> "Tài khoản không đủ quyền thực hiện thao tác.";
            default -> action != null && action.startsWith("LOGIN")
                ? "Thông tin đăng nhập chưa đúng hoặc phiên không hợp lệ."
                : "Hệ thống ghi nhận hoạt động này cần được kiểm tra.";
        };
    }

    private static String translateTargetType(String targetType) {
        if (targetType == null) return null;
        if (!isTechnicalCode(targetType)) return targetType;
        return switch (targetType) {
            case "SYSTEM_ACCOUNT", "ACCOUNT" -> "Tài khoản";
            case "GATE" -> "Trạm gác";
            case "PARKING_SESSION" -> "Lượt xe";
            case "PAYMENT", "PAYMENT_TRANSACTION" -> "Giao dịch";
            case "VEHICLE" -> "Phương tiện";
            case "RESIDENT", "HOUSEHOLD" -> "Cư dân";
            case "EVIDENCE" -> "Hình ảnh ghi nhận";
            default -> "Đối tượng liên quan";
        };
    }

    private static String translateVehicleType(String type) {
        if (type == null) return null;
        return switch (type.trim().toUpperCase(Locale.ROOT)) {
            case "CAR", "AUTO", "AUTOMOBILE" -> "Ô tô";
            case "MOTORBIKE", "MOTORCYCLE", "BIKE" -> "Xe máy";
            case "BICYCLE" -> "Xe đạp";
            case "EBIKE", "ELECTRIC_BIKE" -> "Xe điện";
            case "TRUCK" -> "Xe tải";
            default -> type;
        };
    }

    private static String translatePaymentMethod(String method) {
        if (method == null) return "-";
        return switch (method.toUpperCase(Locale.ROOT)) {
            case "CASH" -> "Tiền mặt";
            case "TRANSFER", "BANK_TRANSFER" -> "Chuyển khoản";
            case "CARD", "CREDIT_CARD" -> "Thẻ";
            case "EWALLET", "E_WALLET", "MOMO", "VNPAY" -> "Ví điện tử";
            default -> "Hình thức khác";
        };
    }

    private static String translateFaceDecision(String decision) {
        if (decision == null) return "Chưa xác định";
        return switch (decision.toUpperCase(Locale.ROOT)) {
            case "REJECT" -> "Không khớp";
            case "REVIEW" -> "Cần xem lại";
            case "MATCH", "ACCEPT" -> "Khớp";
            default -> "Chưa xác định";
        };
    }

    private static String translateQualityStatus(String status) {
        if (status == null) return "Cần kiểm tra";
        return switch (status.toUpperCase(Locale.ROOT)) {
            case "WARNING" -> "Cảnh báo";
            case "REJECT", "FAILED", "FAIL" -> "Không đạt";
            case "PASS", "OK" -> "Đạt";
            default -> "Cần kiểm tra";
        };
    }

    private static String translateLiveness(String status) {
        if (status == null) return "Chưa xác định";
        return switch (status.toUpperCase(Locale.ROOT)) {
            case "PASS" -> "Đạt";
            case "FAIL" -> "Chưa đạt";
            case "REVIEW" -> "Cần xem lại";
            default -> "Chưa xác định";
        };
    }

    public static String translateOperation(String operation) {
        if (operation == null) return "Ghi nhận vận hành";
        return switch (operation.toUpperCase(Locale.ROOT)) {
            case "ENTRY" -> "Xe vào";
            case "EXIT" -> "Xe ra";
            case "AUDIT" -> "Ghi nhận an ninh";
            case "PAYMENT" -> "Giao dịch";
            case "GATE" -> "Trạm gác";
            default -> "Ghi nhận vận hành";
        };
    }

    private static String translateReasonCode(String code) {
        if (code == null) return "-";
        return switch (code) {
            case "PLATE_NOT_DETECTED" -> "Không đọc được biển số";
            case "PLATE_CORRECTION" -> "Biển số nhận dạng chưa đúng";
            case "VEHICLE_TYPE_CORRECTION" -> "Loại xe nhận dạng chưa đúng";
            case "FACE_REVIEW" -> "Khuôn mặt cần xem xét lại";
            case "FACE_FAILED" -> "Đối chiếu khuôn mặt thất bại";
            case "REGISTRATION_IMAGE_MISSING" -> "Thiếu ảnh đăng ký cư dân";
            default -> code;
        };
    }

    private static String translateQualityWarning(String code) {
        if (code == null) return "-";
        return switch (code) {
            case "FACE_DARK", "PLATE_DARK" -> "Ảnh quá tối";
            case "FACE_OVEREXPOSED", "PLATE_OVEREXPOSED" -> "Ảnh quá sáng";
            case "FACE_GLARE", "PLATE_GLARE" -> "Bị lóa sáng";
            case "FACE_BLURRY", "PLATE_BLURRY" -> "Ảnh bị mờ";
            case "FACE_TOO_SMALL", "PLATE_TOO_SMALL" -> "Vùng ảnh quá nhỏ";
            case "PLATE_TILTED" -> "Biển số bị nghiêng";
            default -> code;
        };
    }
}

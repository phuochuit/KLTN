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
            if (ev.getOriginalPlateNumber() != null) details.put("Biển số gốc AI", ev.getOriginalPlateNumber());
            if (ev.getCorrectedPlateNumber() != null) details.put("Biển số đã sửa", ev.getCorrectedPlateNumber());
            if (ev.getOriginalVehicleType() != null) details.put("Loại xe gốc", ev.getOriginalVehicleType());
            if (ev.getCorrectedVehicleType() != null) details.put("Loại xe đã sửa", ev.getCorrectedVehicleType());

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
                ev.getOutcome() != null ? ev.getOutcome().name() : "APPROVED",
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
                if (sa.getTargetType() != null) details.put("Đối tượng", sa.getTargetType());
                if (sa.getTargetReference() != null) details.put("Mã tham chiếu", sa.getTargetReference());
                if (sa.getEvidenceReference() != null) details.put("Bằng chứng", sa.getEvidenceReference());

                String sev = "FAILED".equalsIgnoreCase(sa.getOutcome()) ? "CRITICAL" : "WARNING";
                allItems.add(new ExceptionReportItemResponse(
                    "AUD-" + sa.getId(),
                    sa.getOccurredAt(),
                    "SECURITY_ALERT",
                    "Cảnh báo bảo mật",
                    sev,
                    sa.getTargetReference() != null ? sa.getTargetReference() : "-",
                    sa.getActorUsername() != null ? sa.getActorUsername() : "SYSTEM",
                    sa.getActorUsername() != null ? sa.getActorUsername() : "Hệ thống",
                    null,
                    "AUDIT",
                    sa.getAction(),
                    sa.getReason() != null ? sa.getReason() : ("Cảnh báo an ninh từ hành động " + sa.getAction()),
                    sa.getOutcome(),
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
                details.put("Hình thức", pt.getPaymentMethod().name());
                if (pt.getReconciliationNote() != null) details.put("Ghi chú đối soát", pt.getReconciliationNote());

                String plate = pt.getParkingSession() != null ? pt.getParkingSession().getEntryPlate() : "-";
                String collector = pt.getCollectedBy() != null ? pt.getCollectedBy().getUsername() : "SYSTEM";
                String collectorName = pt.getCollectedBy() != null ? pt.getCollectedBy().getFullName() : collector;

                boolean isCritical = pt.getStatus() == PaymentStatus.VOIDED || pt.getStatus() == PaymentStatus.REFUNDED;

                allItems.add(new ExceptionReportItemResponse(
                    "PAY-" + pt.getId(),
                    pt.getPaidAt(),
                    "PAYMENT_ISSUE",
                    "Lỗi thanh toán / Đối soát",
                    isCritical ? "CRITICAL" : "WARNING",
                    plate,
                    collector,
                    collectorName,
                    null,
                    "PAYMENT",
                    pt.getStatus().name(),
                    pt.getReconciliationNote() != null ? pt.getReconciliationNote() : ("Giao dịch thanh toán: " + pt.getStatus().getDisplayName()),
                    pt.getStatus().name(),
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
            if (fe.getFaceDecision() != null) details.put("Quyết định AI", fe.getFaceDecision());
            if (fe.getFaceSimilarity() != null) details.put("Độ tương đồng", String.format("%.3f", fe.getFaceSimilarity().doubleValue()));
            if (fe.getFaceMatchThreshold() != null) details.put("Ngưỡng cho phép", String.format("%.3f", fe.getFaceMatchThreshold().doubleValue()));

            String opUser = fe.getAccount() != null ? fe.getAccount().getUsername() : "UNKNOWN";
            String opName = fe.getAccount() != null ? fe.getAccount().getFullName() : opUser;

            allItems.add(new ExceptionReportItemResponse(
                "FAC-" + fe.getId().substring(0, Math.min(fe.getId().length(), 12)),
                fe.getCreatedAt(),
                "FACE_REJECT",
                "Khuôn mặt bất thường",
                "REJECT".equalsIgnoreCase(fe.getFaceDecision()) ? "CRITICAL" : "WARNING",
                fe.getPlateNumber() != null ? fe.getPlateNumber() : "-",
                opUser,
                opName,
                null,
                fe.getOperation() != null ? fe.getOperation().name() : "GATE",
                fe.getFaceDecision(),
                "Đối chiếu khuôn mặt trả về kết quả " + fe.getFaceDecision() + " (Độ tương đồng: "
                    + (fe.getFaceSimilarity() != null ? String.format("%.3f", fe.getFaceSimilarity().doubleValue()) : "-") + ")",
                fe.getFaceDecision(),
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
            details.put("Mức chất lượng", status);
            details.put("Mã cảnh báo", String.join(", ", codes));
            details.put("Diễn giải", readable);
            if (qe.getQualityVersion() != null) details.put("Phiên bản chấm chất lượng", qe.getQualityVersion());
            if (qe.getLivenessStatus() != null) details.put("Liveness", qe.getLivenessStatus().name());

            String opUser = qe.getAccount() != null ? qe.getAccount().getUsername() : "UNKNOWN";
            String opName = qe.getAccount() != null && qe.getAccount().getFullName() != null
                ? qe.getAccount().getFullName() : opUser;

            allItems.add(new ExceptionReportItemResponse(
                "QUA-" + qe.getId().substring(0, Math.min(qe.getId().length(), 12)),
                qe.getCreatedAt(),
                "AI_QUALITY",
                "Cảnh báo chất lượng AI",
                "REJECT".equalsIgnoreCase(status) ? "CRITICAL" : "WARNING",
                qe.getPlateNumber() != null ? qe.getPlateNumber() : "-",
                opUser,
                opName,
                null,
                qe.getOperation() != null ? qe.getOperation().name() : "GATE",
                status,
                "AI cảnh báo chất lượng ảnh: " + readable,
                status,
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

    private static String translateReasonCode(String code) {
        if (code == null) return "-";
        return switch (code) {
            case "PLATE_NOT_DETECTED" -> "AI không đọc được biển số";
            case "PLATE_CORRECTION" -> "AI đọc sai biển số";
            case "VEHICLE_TYPE_CORRECTION" -> "AI nhận sai loại xe";
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

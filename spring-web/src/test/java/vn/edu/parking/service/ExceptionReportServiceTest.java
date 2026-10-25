package vn.edu.parking.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import vn.edu.parking.domain.*;
import vn.edu.parking.repository.*;
import vn.edu.parking.web.dto.ExceptionReportPageResponse;

class ExceptionReportServiceTest {
    private ManualOverrideEventRepository manualOverrides;
    private SecurityAuditRepository securityAudits;
    private PaymentTransactionRepository paymentTransactions;
    private GateEvidenceRepository gateEvidences;
    private ExceptionReportService service;

    @BeforeEach
    void setUp() {
        manualOverrides = mock(ManualOverrideEventRepository.class);
        securityAudits = mock(SecurityAuditRepository.class);
        paymentTransactions = mock(PaymentTransactionRepository.class);
        gateEvidences = mock(GateEvidenceRepository.class);
        service = new ExceptionReportService(manualOverrides, securityAudits, paymentTransactions, gateEvidences);
    }

    @Test
    void aggregatesExceptionsFromAllSourcesAndCalculatesSummary() throws Exception {
        Instant now = Instant.now();
        Instant from = now.minus(5, ChronoUnit.DAYS);

        // 1. Mock Manual Override
        SystemAccount staff = new SystemAccount("nv_thinh", "hash", AccountRole.GATE_STAFF, false);
        staff.setFullName("Lục Xuân Thịnh");
        DesktopSession ds = new DesktopSession("sid-1", staff, now, now.plusSeconds(3600), "gate-1");

        ManualOverrideEvent override = new ManualOverrideEvent(
            EvidenceOperation.ENTRY, ManualOverrideOutcome.APPROVED,
            "PLATE_CORRECTION", "Nhập lại biển số do lóa đèn", staff, ds,
            null, null, null, null, "59A-11111", "59A-22222", "CAR", "CAR", now.minus(1, ChronoUnit.HOURS));

        when(manualOverrides.findTop500ByCreatedAtBetweenOrderByCreatedAtDesc(any(), any()))
            .thenReturn(List.of(override));

        // 2. Mock Security Audit
        SecurityAuditEvent auditEvent = new SecurityAuditEvent(
            1L, "nv_thinh", "TAMPER_DETECTED", "GATE", "GATE_1", "FAILED",
            "Phát hiện can thiệp barrier bất thường", "REF-001", now.minus(2, ChronoUnit.HOURS));

        when(securityAudits.findTop500ByOccurredAtBetweenOrderByOccurredAtDesc(any(), any()))
            .thenReturn(List.of(auditEvent));

        // 3. Mock Payment Transaction
        PaymentTransaction pt = new PaymentTransaction(
            "REC-9999", PaymentType.PARKING_EXIT, PaymentMethod.CASH,
            BigDecimal.valueOf(20000), "EXT-1", null, null, staff, null, now.minus(3, ChronoUnit.HOURS));
        pt.reconcile(staff, now, "Phát hiện chênh lệch tiền mặt cần kiểm tra");

        when(paymentTransactions.findByPaidAtBetweenOrderByPaidAtDesc(any(), any()))
            .thenReturn(List.of(pt));

        // 4. Mock Face Exception
        GateEvidence faceEv = new GateEvidence(
            "fe-123456789012", EvidenceType.FACE, EvidenceOperation.EXIT,
            EvidenceSourceKind.IMAGE, staff, ds, "req-1", "v1", "sha", now.minus(4, ChronoUnit.HOURS), now.plusSeconds(3600));
        faceEv.setFaceResult("up-1", null, "REJECT", 0.32, 0.45, 0.35);

        when(gateEvidences.findRecentFaceExceptions(eq(EvidenceType.FACE), anyList(), any(), any()))
            .thenReturn(List.of(faceEv));

        // Execute
        ExceptionReportPageResponse res = service.getExceptionReport(from, now, "ALL", "ALL", null, null);

        // Verify
        assertEquals(4, res.totalItems());
        assertEquals(4, res.summary().totalExceptions());
        assertEquals(1, res.summary().manualOverridesCount());
        assertEquals(1, res.summary().securityAlertsCount());
        assertEquals(1, res.summary().paymentIssuesCount());
        assertEquals(1, res.summary().faceRejectsCount());

        // Verify sorting by timestamp desc
        assertTrue(res.items().get(0).timestamp().isAfter(res.items().get(1).timestamp()));
    }

    @Test
    void filtersByCategoryCorrectly() {
        Instant now = Instant.now();
        SystemAccount staff = new SystemAccount("staff1", "hash", AccountRole.GATE_STAFF, false);
        DesktopSession ds = new DesktopSession("sid-1", staff, now, now.plusSeconds(3600), "gate");

        ManualOverrideEvent override = new ManualOverrideEvent(
            EvidenceOperation.ENTRY, ManualOverrideOutcome.APPROVED,
            "PLATE_NOT_DETECTED", "Không thấy biển", staff, ds,
            null, null, null, null, null, "59B-99999", null, "MOTORBIKE", now);

        when(manualOverrides.findTop500ByCreatedAtBetweenOrderByCreatedAtDesc(any(), any()))
            .thenReturn(List.of(override));
        when(securityAudits.findTop500ByOccurredAtBetweenOrderByOccurredAtDesc(any(), any()))
            .thenReturn(Collections.emptyList());
        when(paymentTransactions.findByPaidAtBetweenOrderByPaidAtDesc(any(), any()))
            .thenReturn(Collections.emptyList());
        when(gateEvidences.findRecentFaceExceptions(any(), anyList(), any(), any()))
            .thenReturn(Collections.emptyList());

        var res = service.getExceptionReport(now.minus(1, ChronoUnit.DAYS), now, "MANUAL_OVERRIDE", "ALL", null, null);

        assertEquals(1, res.totalItems());
        assertEquals("MANUAL_OVERRIDE", res.items().get(0).category());
        assertEquals("59B-99999", res.items().get(0).plateNumber());
    }
}

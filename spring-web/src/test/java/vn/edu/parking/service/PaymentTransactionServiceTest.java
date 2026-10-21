package vn.edu.parking.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import vn.edu.parking.domain.*;
import vn.edu.parking.repository.*;

class PaymentTransactionServiceTest {
    private final PaymentTransactionRepository transactions = mock(PaymentTransactionRepository.class);
    private final SystemAccountRepository accounts = mock(SystemAccountRepository.class);
    private final GateShiftAssignmentRepository shifts = mock(GateShiftAssignmentRepository.class);
    private final SecurityAuditService audit = mock(SecurityAuditService.class);
    private final PaymentTransactionService service =
        new PaymentTransactionService(transactions, accounts, shifts, audit);
    private final SystemAccount collector = mock(SystemAccount.class);

    @BeforeEach
    void authenticateCollector() {
        AccountPrincipal principal = mock(AccountPrincipal.class);
        when(principal.getAccountId()).thenReturn(7L);
        when(principal.getUsername()).thenReturn("gate-7");
        SecurityContextHolder.getContext().setAuthentication(
            new UsernamePasswordAuthenticationToken(principal, null, List.of()));
        when(accounts.findById(7L)).thenReturn(Optional.of(collector));
        when(collector.getId()).thenReturn(7L);
        when(transactions.save(any(PaymentTransaction.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));
    }

    @AfterEach
    void clearAuthentication() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void recordsCashExitUsingServerFeeAndActiveShift() {
        ParkingSession session = mock(ParkingSession.class);
        GateShiftAssignment shift = mock(GateShiftAssignment.class);
        when(session.getId()).thenReturn(21L);
        when(session.getFee()).thenReturn(new BigDecimal("15000"));
        when(shifts.findByAccount_IdAndStartsAtLessThanEqualAndEndsAtGreaterThanOrderByStartsAtDesc(
            any(), any(), any())).thenReturn(List.of(shift));

        PaymentTransaction result = service.recordParkingExit(session, PaymentMethod.CASH, "ignored");

        assertThat(result.getPaymentType()).isEqualTo(PaymentType.PARKING_EXIT);
        assertThat(result.getAmount()).isEqualByComparingTo("15000");
        assertThat(result.getExternalReference()).isNull();
        assertThat(result.getGateShiftAssignment()).isSameAs(shift);
        assertThat(result.getCollectedBy()).isSameAs(collector);
        assertThat(result.getReceiptNumber()).startsWith("PT-");
        verify(audit).recordCurrent("PAYMENT_COLLECT", "PAYMENT_TRANSACTION",
            result.getReceiptNumber(), "SUCCESS", null, null);
    }

    @Test
    void transferRequiresExternalReference() {
        ParkingSession session = mock(ParkingSession.class);
        when(session.getId()).thenReturn(22L);
        when(session.getFee()).thenReturn(new BigDecimal("20000"));

        assertThatThrownBy(() -> service.recordParkingExit(session, PaymentMethod.BANK_TRANSFER, " "))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("mã giao dịch");
        verify(transactions, never()).save(any());
    }

    @Test
    void rejectsDuplicateExitPayment() {
        ParkingSession session = mock(ParkingSession.class);
        when(session.getId()).thenReturn(23L);
        when(transactions.existsByParkingSession_Id(23L)).thenReturn(true);

        assertThatThrownBy(() -> service.recordParkingExit(session, PaymentMethod.CASH, null))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("đã có giao dịch");
    }

    @Test
    void reconcilesPaidTransactionOnce() {
        ParkingSession session = mock(ParkingSession.class);
        PaymentTransaction transaction = new PaymentTransaction("PT-TEST", PaymentType.PARKING_EXIT,
            PaymentMethod.CASH, new BigDecimal("10000"), null, session, null, collector, null, Instant.now());
        when(transactions.findDetailedById(31L)).thenReturn(Optional.of(transaction));

        PaymentTransaction result = service.reconcile(31L, " Đã kiểm tra tiền mặt ");

        assertThat(result.getStatus()).isEqualTo(PaymentStatus.RECONCILED);
        assertThat(result.getReconciliationNote()).isEqualTo("Đã kiểm tra tiền mặt");
        assertThat(result.getReconciledBy()).isSameAs(collector);
        verify(audit).recordCurrent("PAYMENT_RECONCILE", "PAYMENT_TRANSACTION",
            "PT-TEST", "SUCCESS", null, null);

        assertThatThrownBy(() -> service.reconcile(31L, "Lần hai"))
            .isInstanceOf(IllegalStateException.class);
    }
}

package vn.edu.parking.service;

import java.math.BigDecimal;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import vn.edu.parking.domain.AccountRole;
import vn.edu.parking.domain.GateShiftAssignment;
import vn.edu.parking.domain.PaymentMethod;
import vn.edu.parking.domain.PaymentTransaction;
import vn.edu.parking.domain.PaymentType;
import vn.edu.parking.domain.SystemAccount;
import vn.edu.parking.repository.GateShiftAssignmentRepository;
import vn.edu.parking.repository.PaymentTransactionRepository;
import vn.edu.parking.repository.SystemAccountRepository;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@TestPropertySource(properties = {
    "spring.datasource.url=jdbc:h2:mem:shift-summary-known-total;DB_CLOSE_DELAY=-1",
    "spring.jpa.hibernate.ddl-auto=create-drop"
})
class ShiftSummaryKnownTotalIntegrationTest {
    @Autowired ShiftSummaryService summaries;
    @Autowired SystemAccountRepository accounts;
    @Autowired GateShiftAssignmentRepository shifts;
    @Autowired PaymentTransactionRepository payments;

    @Test
    void calculatesKnownTotalsByPaymentMethodAndPendingStatus() {
        SystemAccount collector = new SystemAccount("known-total-gate", "Nhân viên known total",
            "test-password-hash", AccountRole.GATE_STAFF, false);
        collector.setEnabled(true);
        collector = accounts.save(collector);
        Instant now = Instant.now();
        GateShiftAssignment shift = shifts.save(new GateShiftAssignment(collector,
            "Ca known total", now.minusSeconds(600), now.plusSeconds(3600), "Đối soát", null));

        payments.save(transaction("KT-CASH", PaymentMethod.CASH, "100000", collector, shift, now));
        PaymentTransaction bank = transaction("KT-BANK", PaymentMethod.BANK_TRANSFER,
            "250000", collector, shift, now.plusSeconds(1));
        bank.reconcile(collector, now.plusSeconds(2), "Đã đối soát chuyển khoản");
        payments.save(bank);
        payments.save(transaction("KT-POS", PaymentMethod.POS, "75000", collector, shift,
            now.plusSeconds(3)));

        ShiftSummaryService.ShiftSummary result = summaries.current(collector.getId()).orElseThrow();

        assertThat(result.cashRevenue()).isEqualByComparingTo("100000");
        assertThat(result.bankTransferRevenue()).isEqualByComparingTo("250000");
        assertThat(result.posRevenue()).isEqualByComparingTo("75000");
        assertThat(result.totalRevenue()).isEqualByComparingTo("425000");
        assertThat(result.pendingReconciliation()).isEqualTo(2);
    }

    private static PaymentTransaction transaction(String receipt, PaymentMethod method,
            String amount, SystemAccount collector, GateShiftAssignment shift, Instant paidAt) {
        return new PaymentTransaction(receipt, PaymentType.PARKING_EXIT, method,
            new BigDecimal(amount), null, null, null, collector, shift, paidAt);
    }
}

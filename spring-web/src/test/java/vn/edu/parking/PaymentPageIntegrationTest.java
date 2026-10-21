package vn.edu.parking;

import java.math.BigDecimal;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import vn.edu.parking.domain.AccountRole;
import vn.edu.parking.domain.PaymentMethod;
import vn.edu.parking.domain.PaymentStatus;
import vn.edu.parking.domain.PaymentTransaction;
import vn.edu.parking.domain.PaymentType;
import vn.edu.parking.domain.SystemAccount;
import vn.edu.parking.repository.PaymentTransactionRepository;
import vn.edu.parking.repository.SystemAccountRepository;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
    "spring.datasource.url=jdbc:h2:mem:payment-page-test;DB_CLOSE_DELAY=-1",
    "spring.jpa.hibernate.ddl-auto=create-drop"
})
@WithMockUser(username = "cashier", roles = "MANAGEMENT")
class PaymentPageIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired PaymentTransactionRepository transactions;
    @Autowired SystemAccountRepository accounts;

    private PaymentTransaction paid;

    @BeforeEach
    void seedPaidTransaction() {
        transactions.deleteAll();
        SystemAccount collector = accounts.findByUsername("cashier").orElseGet(() -> {
            var account = new SystemAccount("cashier", "Thu ngân kiểm thử",
                "test-password-hash", AccountRole.GATE_STAFF, false);
            account.setEnabled(true);
            return accounts.save(account);
        });
        paid = transactions.save(new PaymentTransaction("PT-TEST-0001", PaymentType.SUBSCRIPTION,
            PaymentMethod.CASH, new BigDecimal("150000"), null, null, null, collector, null,
            Instant.now()));
    }

    @Test
    void rendersReconcileFormWithRealTransactionId() throws Exception {
        mvc.perform(get("/payments"))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString(
                "action=\"/payments/" + paid.getId() + "/reconcile\"")))
            .andExpect(content().string(not(containsString("item.id"))));
    }

    @Test
    void reconcileClosesTransactionAndRedirectsWithMessage() throws Exception {
        mvc.perform(post("/payments/" + paid.getId() + "/reconcile").with(csrf())
                .param("note", "Đã kiểm tra két"))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/payments"))
            .andExpect(flash().attributeExists("message"));

        PaymentTransaction stored = transactions.findById(paid.getId()).orElseThrow();
        assertEquals(PaymentStatus.RECONCILED, stored.getStatus());
        assertEquals("Đã kiểm tra két", stored.getReconciliationNote());
    }

    @Test
    void reconciliationReportSerializesReconciledRow() throws Exception {
        mvc.perform(post("/payments/" + paid.getId() + "/reconcile").with(csrf())
                .param("note", "Đối soát cuối ngày"))
            .andExpect(status().is3xxRedirection());

        mvc.perform(get("/payments/reports/reconciliation"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.count").value(1))
            .andExpect(jsonPath("$.amount").value(150000))
            .andExpect(jsonPath("$.transactions[0].receiptNumber").value("PT-TEST-0001"))
            .andExpect(jsonPath("$.transactions[0].status").value("RECONCILED"))
            .andExpect(jsonPath("$.transactions[0].collectedBy").value("cashier"));
    }

    @Test
    void missingReceiptReturnsNotFound() throws Exception {
        mvc.perform(get("/payments/receipts/{id}", 999999L))
            .andExpect(status().isNotFound());
    }
}

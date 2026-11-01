package vn.edu.parking;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.SoftAssertions.assertSoftly;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import vn.edu.parking.domain.AccountRole;
import vn.edu.parking.domain.CardStatus;
import vn.edu.parking.domain.PassType;
import vn.edu.parking.domain.PaymentMethod;
import vn.edu.parking.domain.ParkingCard;
import vn.edu.parking.domain.SubscriptionPayment;
import vn.edu.parking.domain.SystemAccount;
import vn.edu.parking.domain.Vehicle;
import vn.edu.parking.domain.VehicleType;
import vn.edu.parking.repository.ParkingCardRepository;
import vn.edu.parking.repository.PaymentTransactionRepository;
import vn.edu.parking.repository.SubscriptionPaymentRepository;
import vn.edu.parking.repository.SystemAccountRepository;
import vn.edu.parking.repository.VehicleRepository;
import vn.edu.parking.service.PaymentTransactionService;

@SpringBootTest
@AutoConfigureMockMvc
@WithMockUser(username = "card-admin", roles = "MANAGEMENT")
class CardWriteTransactionIntegrationTest {
    private static final LocalDate PERIOD_START = LocalDate.of(2026, 11, 1);
    private static final int DURATION_DAYS = 30;

    @DynamicPropertySource
    static void isolatedProperties(DynamicPropertyRegistry registry) throws java.io.IOException {
        LoginTestIsolation.register(registry, "card-write-integrity");
    }

    @Autowired MockMvc mvc;
    @Autowired VehicleRepository vehicles;
    @Autowired ParkingCardRepository cards;
    @Autowired SubscriptionPaymentRepository subscriptions;
    @Autowired PaymentTransactionRepository transactions;
    @Autowired SystemAccountRepository accounts;
    @Autowired JdbcTemplate jdbc;
    @org.springframework.test.context.bean.override.mockito.MockitoSpyBean
    PaymentTransactionService paymentTransactionSpy;

    @BeforeEach
    void seedCollectorAccount() {
        if (accounts.findByUsername("card-admin").isEmpty()) {
            accounts.saveAndFlush(new SystemAccount("card-admin", "Synthetic Card Collector",
                "test-password-hash", AccountRole.MANAGEMENT, false));
        }
    }

    @Test
    void cardsPageExposesServerMonthlyPricesForEachVehicleType() throws Exception {
        MvcResult result = mvc.perform(get("/cards")).andExpect(status().isOk()).andReturn();

        Map<?, ?> monthlyPrices = (Map<?, ?>) result.getModelAndView().getModel().get("monthlyPrices");
        assertThat(monthlyPrices).isNotNull();
        assertThat(monthlyPrices.get("MOTORBIKE")).isEqualTo(BigDecimal.valueOf(150_000));
    }

    @Test
    void monthlyPassPersistsCardPeriodAndLinkedReceipt() throws Exception {
        Vehicle vehicle = vehicle();

        MvcResult result = saveMonthly(vehicle, "CASH", null).andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/cards")).andReturn();

        ParkingCard card = cards.findByVehicleId(vehicle.getId()).orElseThrow();
        assertThat(card.getPassType()).isEqualTo(PassType.MONTHLY);
        assertThat(card.getValidFrom()).isEqualTo(PERIOD_START);
        assertThat(card.getValidUntil()).isEqualTo(PERIOD_START.plusDays(DURATION_DAYS - 1L));
        assertThat(card.getSubscriptionFee()).isEqualByComparingTo("150000");
        var cardPayments = subscriptions.findAll().stream()
            .filter(item -> item.getParkingCard().getId().equals(card.getId())).toList();
        assertThat(cardPayments).hasSize(1);
        SubscriptionPayment payment = cardPayments.getFirst();
        assertThat(payment.getPeriodStart()).isEqualTo(PERIOD_START);
        assertThat(payment.getPeriodEnd()).isEqualTo(PERIOD_START.plusDays(DURATION_DAYS - 1L));
        assertThat(payment.getAmount()).isEqualByComparingTo("150000");
        assertThat(payment.isPendingPayment()).isFalse();
        assertThat(transactions.existsBySubscriptionPayment_Id(payment.getId())).isTrue();
        Long linkedTransactionId = jdbc.queryForObject(
            "select payment_transaction_id from subscription_payments where id = ?",
            Long.class, payment.getId());
        assertThat(linkedTransactionId).isNotNull();
        String receiptNumber = transactions.findDetailedById(linkedTransactionId).orElseThrow().getReceiptNumber();
        assertThat(receiptNumber).isNotBlank();
        assertThat(result.getFlashMap().get("message")).isEqualTo(
            "Đã kích hoạt gói 30 ngày đến 2026-11-30. Biên nhận: " + receiptNumber);
    }

    @Test
    void perVisitPassPersistsCardWithoutSubscriptionOrPayment() throws Exception {
        WriteCounts before = writeCounts();
        Vehicle vehicle = vehicle();

        MvcResult result = mvc.perform(post("/cards").with(csrf())
                .param("vehicleId", vehicle.getId().toString())
                .param("status", CardStatus.ACTIVE.name())
                .param("passType", PassType.PER_VISIT.name()))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/cards")).andReturn();

        ParkingCard card = cards.findByVehicleId(vehicle.getId()).orElseThrow();
        assertThat(card.getPassType()).isEqualTo(PassType.PER_VISIT);
        assertThat(card.getValidFrom()).isNull();
        assertThat(card.getValidUntil()).isNull();
        assertThat(card.getSubscriptionFee()).isZero();
        assertThat(cards.count()).isEqualTo(before.cards() + 1);
        assertThat(subscriptions.count()).isEqualTo(before.subscriptions());
        assertThat(transactions.count()).isEqualTo(before.transactions());
        assertThat(result.getFlashMap().get("message")).isEqualTo("Đã lưu thẻ vé lượt");
    }

    @Test
    void missingPaymentMethodReturnsRetrySafeErrorAndRollsBackCardAndSubscriptionWrites() throws Exception {
        WriteCounts before = writeCounts();
        Vehicle vehicle = vehicle();

        MvcResult result = saveMonthly(vehicle, null, null).andExpect(status().isBadRequest()).andReturn();

        assertWriteError(result, true, "Vui lòng chọn phương thức thanh toán.");

        assertNoCardOperationWrites(before, vehicle.getId());
    }

    @Test
    void missingExternalReferenceReturnsRetrySafeErrorAndRollsBackCardAndSubscriptionWrites() throws Exception {
        WriteCounts before = writeCounts();
        Vehicle vehicle = vehicle();

        MvcResult result = saveMonthly(vehicle, PaymentMethod.BANK_TRANSFER.name(), null)
            .andExpect(status().isBadRequest()).andReturn();

        assertWriteError(result, true, "Vui lòng nhập mã giao dịch chuyển khoản/POS.");

        assertNoCardOperationWrites(before, vehicle.getId());
    }

    @Test
    void failureWhileLinkingReceiptRollsBackNewCardPeriodAndTransaction() throws Exception {
        WriteCounts before = writeCounts();
        Vehicle vehicle = vehicle();
        failAfterPaymentTransactionRecorded();

        MvcResult result = saveMonthly(vehicle, PaymentMethod.CASH.name(), null)
            .andExpect(status().isServiceUnavailable()).andReturn();

        assertWriteError(result, false,
            "Không xác nhận được kết quả lưu. Hãy tải lại dữ liệu trước khi quyết định có gửi lại hay không.");

        assertNoCardOperationWrites(before, vehicle.getId());
    }

    @Test
    void failureWhileLinkingReceiptRollsBackExistingCardUpdate() throws Exception {
        Vehicle vehicle = vehicle();
        ParkingCard existing = new ParkingCard();
        existing.setCardCode(vehicle.getPlateNumber());
        existing.setVehicle(vehicle);
        existing.setStatus(CardStatus.BLOCKED);
        existing.setPassType(PassType.PER_VISIT);
        existing = cards.saveAndFlush(existing);
        Long cardId = existing.getId();
        WriteCounts before = writeCounts();
        failAfterPaymentTransactionRecorded();

        MvcResult result = saveMonthly(vehicle, PaymentMethod.CASH.name(), null)
            .andExpect(status().isServiceUnavailable()).andReturn();

        assertWriteError(result, false,
            "Không xác nhận được kết quả lưu. Hãy tải lại dữ liệu trước khi quyết định có gửi lại hay không.");

        ParkingCard unchanged = cards.findById(cardId).orElseThrow();
        assertSoftly(softly -> {
            softly.assertThat(unchanged.getStatus()).isEqualTo(CardStatus.BLOCKED);
            softly.assertThat(unchanged.getPassType()).isEqualTo(PassType.PER_VISIT);
            softly.assertThat(unchanged.getValidFrom()).isNull();
            softly.assertThat(unchanged.getValidUntil()).isNull();
            softly.assertThat(unchanged.getSubscriptionFee()).isZero();
            softly.assertThat(cards.count()).isEqualTo(before.cards());
            softly.assertThat(subscriptions.count()).isEqualTo(before.subscriptions());
            softly.assertThat(transactions.count()).isEqualTo(before.transactions());
        });
    }

    private ResultActions saveMonthly(Vehicle vehicle, String method, String reference) throws Exception {
        var request = post("/cards").with(csrf())
            .param("vehicleId", vehicle.getId().toString())
            .param("status", CardStatus.ACTIVE.name())
            .param("passType", PassType.MONTHLY.name())
            .param("startDate", PERIOD_START.toString())
            .param("durationDays", Integer.toString(DURATION_DAYS));
        if (method != null) request.param("paymentMethod", method);
        if (reference != null) request.param("paymentReference", reference);
        return mvc.perform(request);
    }

    private void assertWriteError(MvcResult result, boolean retrySafe, String message) {
        var modelAndView = result.getModelAndView();
        assertThat(modelAndView).isNotNull();
        assertThat(modelAndView.getViewName()).isEqualTo("management-write-error");
        assertThat(modelAndView.getModel())
            .containsEntry("returnPath", "/cards")
            .containsEntry("retrySafe", retrySafe)
            .containsEntry("message", message);
    }

    private Vehicle vehicle() {
        Vehicle vehicle = new Vehicle();
        vehicle.setPlateNumber("CARD" + UUID.randomUUID().toString().replace("-", "").substring(0, 8));
        vehicle.setOwnerName("Synthetic card test");
        vehicle.setVehicleType(VehicleType.MOTORBIKE);
        return vehicles.saveAndFlush(vehicle);
    }

    private WriteCounts writeCounts() {
        return new WriteCounts(cards.count(), subscriptions.count(), transactions.count());
    }

    private void assertNoCardOperationWrites(WriteCounts expected, Long vehicleId) {
        assertSoftly(softly -> {
            softly.assertThat(cards.findByVehicleId(vehicleId)).isEmpty();
            softly.assertThat(cards.count()).isEqualTo(expected.cards());
            softly.assertThat(subscriptions.count()).isEqualTo(expected.subscriptions());
            softly.assertThat(transactions.count()).isEqualTo(expected.transactions());
        });
    }

    private void failAfterPaymentTransactionRecorded() {
        doAnswer(invocation -> {
            invocation.callRealMethod();
            throw new DataAccessResourceFailureException("synthetic receipt-link failure");
        }).when(paymentTransactionSpy).recordSubscription(
            any(SubscriptionPayment.class), any(PaymentMethod.class), any());
    }

    private record WriteCounts(long cards, long subscriptions, long transactions) { }
}

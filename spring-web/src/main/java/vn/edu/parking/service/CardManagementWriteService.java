package vn.edu.parking.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.edu.parking.domain.CardStatus;
import vn.edu.parking.domain.PassType;
import vn.edu.parking.domain.PaymentMethod;
import vn.edu.parking.domain.PaymentTransaction;
import vn.edu.parking.domain.ParkingCard;
import vn.edu.parking.domain.SubscriptionPayment;
import vn.edu.parking.domain.Vehicle;
import vn.edu.parking.repository.ParkingCardRepository;
import vn.edu.parking.repository.PricingRuleRepository;
import vn.edu.parking.repository.SubscriptionPaymentRepository;
import vn.edu.parking.repository.VehicleRepository;

@Service
public class CardManagementWriteService {
    private final VehicleRepository vehicles;
    private final ParkingCardRepository cards;
    private final PricingRuleRepository prices;
    private final SubscriptionPaymentRepository payments;
    private final PaymentTransactionService paymentTransactions;

    public CardManagementWriteService(VehicleRepository vehicles, ParkingCardRepository cards,
            PricingRuleRepository prices, SubscriptionPaymentRepository payments,
            PaymentTransactionService paymentTransactions) {
        this.vehicles = vehicles;
        this.cards = cards;
        this.prices = prices;
        this.payments = payments;
        this.paymentTransactions = paymentTransactions;
    }

    @Transactional
    public SaveResult saveCard(Long vehicleId, CardStatus status, PassType passType, LocalDate startDate,
            int durationDays, PaymentMethod paymentMethod, String paymentReference) {
        Vehicle vehicle = vehicles.findById(vehicleId).orElseThrow();
        String plateIdentifier = PlateNormalizer.normalize(vehicle.getPlateNumber());
        ParkingCard card = cards.findByVehicleId(vehicleId).orElseGet(ParkingCard::new);
        card.setCardCode(plateIdentifier);
        card.setVehicle(vehicle);
        card.setStatus(status);
        card.setPassType(passType);

        if (passType == PassType.MONTHLY) {
            LocalDate from = startDate == null ? LocalDate.now() : startDate;
            LocalDate until = from.plusDays(durationDays - 1L);
            BigDecimal amount = prices.findByVehicleType(vehicle.getVehicleType())
                    .orElseThrow(
                            () -> new IllegalStateException("Chưa cấu hình bảng giá cho " + vehicle.getVehicleType()))
                    .getMonthlyPrice();
            if (amount.signum() <= 0) {
                throw new IllegalStateException(
                        "Chưa cấu hình giá gói tháng cho " + vehicle.getVehicleType().getDisplayName());
            }
            card.setValidFrom(from);
            card.setValidUntil(until);
            card.setSubscriptionFee(amount);
            card = cards.save(card);

            SubscriptionPayment payment = new SubscriptionPayment();
            payment.setParkingCard(card);
            payment.setPeriodStart(from);
            payment.setPeriodEnd(until);
            payment.setAmount(amount);
            payment.setPaidAt(LocalDateTime.now());
            payment.setPendingPayment(true);
            payment = payments.save(payment);
            PaymentTransaction transaction = paymentTransactions.recordSubscription(
                payment, paymentMethod, paymentReference);
            payment.setPendingPayment(false);
            payment.setPaymentTransaction(transaction);
            payments.save(payment);
            return new SaveResult(until, transaction.getReceiptNumber());
        }

        card.setValidFrom(null);
        card.setValidUntil(null);
        card.setSubscriptionFee(BigDecimal.ZERO);
        cards.save(card);
        return new SaveResult(null, null);
    }

    public record SaveResult(LocalDate validUntil, String receiptNumber) { }
}

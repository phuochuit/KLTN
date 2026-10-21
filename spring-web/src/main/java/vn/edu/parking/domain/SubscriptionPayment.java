package vn.edu.parking.domain;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "subscription_payments")
public class SubscriptionPayment {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    private ParkingCard parkingCard;
    @Column(nullable = false)
    private LocalDate periodStart;
    @Column(nullable = false)
    private LocalDate periodEnd;
    @Column(nullable = false, precision = 12, scale = 0)
    private BigDecimal amount;
    @Column(nullable = false)
    private LocalDateTime paidAt;
    @Column(name = "pending_payment", nullable = false, columnDefinition = "boolean default false")
    private boolean pendingPayment;
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "payment_transaction_id")
    private PaymentTransaction paymentTransaction;

    public Long getId() { return id; }
    public ParkingCard getParkingCard() { return parkingCard; }
    public void setParkingCard(ParkingCard parkingCard) { this.parkingCard = parkingCard; }
    public LocalDate getPeriodStart() { return periodStart; }
    public void setPeriodStart(LocalDate periodStart) { this.periodStart = periodStart; }
    public LocalDate getPeriodEnd() { return periodEnd; }
    public void setPeriodEnd(LocalDate periodEnd) { this.periodEnd = periodEnd; }
    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }
    public LocalDateTime getPaidAt() { return paidAt; }
    public void setPaidAt(LocalDateTime paidAt) { this.paidAt = paidAt; }
    public boolean isPendingPayment() { return pendingPayment; }
    public void setPendingPayment(boolean pendingPayment) { this.pendingPayment = pendingPayment; }
    public PaymentTransaction getPaymentTransaction() { return paymentTransaction; }
    public void setPaymentTransaction(PaymentTransaction paymentTransaction) {
        this.paymentTransaction = paymentTransaction;
    }
}

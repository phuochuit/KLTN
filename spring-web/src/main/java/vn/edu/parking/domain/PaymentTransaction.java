package vn.edu.parking.domain;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "payment_transactions", uniqueConstraints = {
    @UniqueConstraint(name = "uk_payment_transactions_receipt", columnNames = "receipt_number"),
    @UniqueConstraint(name = "uk_payment_transactions_parking_session", columnNames = "parking_session_id"),
    @UniqueConstraint(name = "uk_payment_transactions_subscription", columnNames = "subscription_payment_id")
})
public class PaymentTransaction {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "receipt_number", nullable = false, length = 40)
    private String receiptNumber;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_type", nullable = false, length = 32)
    private PaymentType paymentType;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_method", nullable = false, length = 32)
    private PaymentMethod paymentMethod;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private PaymentStatus status;

    @Column(nullable = false, precision = 12, scale = 0)
    private BigDecimal amount;

    @Column(name = "external_reference", length = 120)
    private String externalReference;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parking_session_id")
    private ParkingSession parkingSession;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "subscription_payment_id")
    private SubscriptionPayment subscriptionPayment;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "collected_by_account_id", nullable = false)
    private SystemAccount collectedBy;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "gate_shift_assignment_id")
    private GateShiftAssignment gateShiftAssignment;

    @Column(name = "paid_at", nullable = false)
    private Instant paidAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reconciled_by_account_id")
    private SystemAccount reconciledBy;

    @Column(name = "reconciled_at")
    private Instant reconciledAt;

    @Column(name = "reconciliation_note", length = 500)
    private String reconciliationNote;

    protected PaymentTransaction() { }

    public PaymentTransaction(String receiptNumber, PaymentType paymentType, PaymentMethod paymentMethod,
            BigDecimal amount, String externalReference, ParkingSession parkingSession,
            SubscriptionPayment subscriptionPayment, SystemAccount collectedBy,
            GateShiftAssignment gateShiftAssignment, Instant paidAt) {
        this.receiptNumber = receiptNumber;
        this.paymentType = paymentType;
        this.paymentMethod = paymentMethod;
        this.status = PaymentStatus.PAID;
        this.amount = amount;
        this.externalReference = externalReference;
        this.parkingSession = parkingSession;
        this.subscriptionPayment = subscriptionPayment;
        this.collectedBy = collectedBy;
        this.gateShiftAssignment = gateShiftAssignment;
        this.paidAt = paidAt;
    }

    public void reconcile(SystemAccount account, Instant at, String note) {
        status = PaymentStatus.RECONCILED;
        reconciledBy = account;
        reconciledAt = at;
        reconciliationNote = note;
    }

    public Long getId() { return id; }
    public String getReceiptNumber() { return receiptNumber; }
    public PaymentType getPaymentType() { return paymentType; }
    public PaymentMethod getPaymentMethod() { return paymentMethod; }
    public PaymentStatus getStatus() { return status; }
    public BigDecimal getAmount() { return amount; }
    public String getExternalReference() { return externalReference; }
    public ParkingSession getParkingSession() { return parkingSession; }
    public SubscriptionPayment getSubscriptionPayment() { return subscriptionPayment; }
    public SystemAccount getCollectedBy() { return collectedBy; }
    public GateShiftAssignment getGateShiftAssignment() { return gateShiftAssignment; }
    public Instant getPaidAt() { return paidAt; }
    public SystemAccount getReconciledBy() { return reconciledBy; }
    public Instant getReconciledAt() { return reconciledAt; }
    public String getReconciliationNote() { return reconciliationNote; }
}

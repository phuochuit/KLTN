package vn.edu.parking.service;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.UUID;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.edu.parking.domain.*;
import vn.edu.parking.repository.*;

@Service
public class PaymentTransactionService {
    private static final DateTimeFormatter RECEIPT_DATE =
        DateTimeFormatter.ofPattern("yyyyMMdd").withZone(ZoneOffset.UTC);
    private final PaymentTransactionRepository transactions;
    private final SystemAccountRepository accounts;
    private final GateShiftAssignmentRepository shifts;
    private final SecurityAuditService audit;

    public PaymentTransactionService(PaymentTransactionRepository transactions,
            SystemAccountRepository accounts, GateShiftAssignmentRepository shifts,
            SecurityAuditService audit) {
        this.transactions = transactions;
        this.accounts = accounts;
        this.shifts = shifts;
        this.audit = audit;
    }

    @Transactional
    public PaymentTransaction recordParkingExit(ParkingSession session, PaymentMethod method,
            String externalReference) {
        if (session == null || session.getId() == null) throw new IllegalArgumentException("Lượt gửi xe không hợp lệ.");
        if (transactions.existsByParkingSession_Id(session.getId()))
            throw new IllegalStateException("Lượt gửi xe đã có giao dịch thanh toán.");
        return create(PaymentType.PARKING_EXIT, method, session.getFee(), externalReference,
            session, null);
    }

    @Transactional
    public PaymentTransaction recordSubscription(SubscriptionPayment subscription, PaymentMethod method,
            String externalReference) {
        if (subscription == null || subscription.getId() == null)
            throw new IllegalArgumentException("Giao dịch gói không hợp lệ.");
        if (transactions.existsBySubscriptionPayment_Id(subscription.getId()))
            throw new IllegalStateException("Giao dịch gói đã được ghi nhận thanh toán.");
        return create(PaymentType.SUBSCRIPTION, method, subscription.getAmount(), externalReference,
            null, subscription);
    }

    @Transactional
    public PaymentTransaction reconcile(long id, String note) {
        PaymentTransaction transaction = transactions.findDetailedById(id)
            .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy giao dịch."));
        if (transaction.getStatus() != PaymentStatus.PAID)
            throw new IllegalStateException("Chỉ giao dịch đã thu mới có thể đối soát.");
        String normalizedNote = normalize(note, 500);
        if (normalizedNote == null) throw new IllegalArgumentException("Vui lòng nhập ghi chú đối soát.");
        transaction.reconcile(currentAccount(), Instant.now(), normalizedNote);
        transaction = transactions.save(transaction);
        audit.recordCurrent("PAYMENT_RECONCILE", "PAYMENT_TRANSACTION",
            transaction.getReceiptNumber(), "SUCCESS", null, null);
        return transaction;
    }

    @Transactional(readOnly = true)
    public java.util.List<PaymentTransaction> recent() {
        return transactions.findTop200ByOrderByPaidAtDesc();
    }

    private PaymentTransaction create(PaymentType type, PaymentMethod method, BigDecimal amount,
            String externalReference, ParkingSession session, SubscriptionPayment subscription) {
        if (method == null) throw new IllegalArgumentException("Vui lòng chọn phương thức thanh toán.");
        if (amount == null || amount.signum() < 0) throw new IllegalArgumentException("Số tiền không hợp lệ.");
        String reference = normalize(externalReference, 120);
        if (method.isExternalReferenceRequired() && reference == null)
            throw new IllegalArgumentException("Vui lòng nhập mã giao dịch chuyển khoản/POS.");
        if (!method.isExternalReferenceRequired()) reference = null;
        SystemAccount collector = currentAccount();
        Instant now = Instant.now();
        GateShiftAssignment shift = shifts
            .findByAccount_IdAndStartsAtLessThanEqualAndEndsAtGreaterThanOrderByStartsAtDesc(
                collector.getId(), now, now).stream().findFirst().orElse(null);
        var transaction = new PaymentTransaction(receipt(now), type, method, amount, reference,
            session, subscription, collector, shift, now);
        transaction = transactions.save(transaction);
        audit.recordCurrent("PAYMENT_COLLECT", "PAYMENT_TRANSACTION",
            transaction.getReceiptNumber(), "SUCCESS", null, null);
        return transaction;
    }

    private SystemAccount currentAccount() {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated())
            throw new IllegalStateException("Không xác định được nhân viên thực hiện giao dịch.");
        if (authentication.getPrincipal() instanceof AccountPrincipal principal) {
            return accounts.findById(principal.getAccountId())
                .orElseThrow(() -> new IllegalStateException("Tài khoản nhân viên không còn tồn tại."));
        }
        return accounts.findByUsername(authentication.getName())
            .orElseThrow(() -> new IllegalStateException("Tài khoản nhân viên không còn tồn tại."));
    }

    private static String receipt(Instant now) {
        return "PT-" + RECEIPT_DATE.format(now) + "-"
            + UUID.randomUUID().toString().replace("-", "").substring(0, 12).toUpperCase(Locale.ROOT);
    }

    private static String normalize(String value, int maxLength) {
        if (value == null || value.isBlank()) return null;
        String normalized = value.trim();
        if (normalized.length() > maxLength)
            throw new IllegalArgumentException("Nội dung vượt quá độ dài cho phép.");
        return normalized;
    }
}

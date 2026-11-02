package vn.edu.parking.web;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpHeaders;
import java.nio.charset.StandardCharsets;
import java.time.format.DateTimeFormatter;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import vn.edu.parking.domain.*;
import vn.edu.parking.repository.PaymentTransactionRepository;
import vn.edu.parking.service.PaymentTransactionService;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Controller
@RequestMapping("/payments")
public class PaymentController {
    private final PaymentTransactionService payments;
    private final PaymentTransactionRepository transactions;

    public PaymentController(PaymentTransactionService payments, PaymentTransactionRepository transactions) {
        this.payments = payments;
        this.transactions = transactions;
    }

    @GetMapping
    String page(Model model) {
        List<PaymentTransaction> recent = payments.recent();
        model.addAttribute("transactions", recent);
        model.addAttribute("paidCount", recent.stream().filter(x -> x.getStatus() == PaymentStatus.PAID).count());
        model.addAttribute("paidAmount", recent.stream().filter(x -> x.getStatus() == PaymentStatus.PAID)
            .map(PaymentTransaction::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add));
        return "payments";
    }

    @PostMapping("/{id}/reconcile")
    String reconcile(@PathVariable long id, @RequestParam(required = false) String note,
            RedirectAttributes redirect) {
        PaymentTransaction transaction = payments.reconcile(id, note);
        redirect.addFlashAttribute("message",
            "Đã chốt đối soát biên nhận " + transaction.getReceiptNumber());
        return "redirect:/payments";
    }

    @GetMapping("/receipts/{id}")
    @ResponseBody
    Map<String, Object> receipt(@PathVariable long id) {
        return transactions.findDetailedById(id).map(transaction -> {
            Map<String, Object> value = new LinkedHashMap<String, Object>();
            value.put("receiptNumber", transaction.getReceiptNumber());
            value.put("paymentType", transaction.getPaymentType().name());
            value.put("paymentMethod", transaction.getPaymentMethod().name());
            value.put("amount", transaction.getAmount());
            value.put("status", transaction.getStatus().name());
            value.put("externalReference", transaction.getExternalReference());
            value.put("paidAt", transaction.getPaidAt());
            value.put("sessionId", transaction.getParkingSession() == null
                ? null : transaction.getParkingSession().getId());
            value.put("subscriptionPaymentId", transaction.getSubscriptionPayment() == null
                ? null : transaction.getSubscriptionPayment().getId());
            value.put("collectedBy", transaction.getCollectedBy() == null
                ? null : transaction.getCollectedBy().getUsername());
            return value;
        }).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
            "Không tìm thấy biên nhận"));
    }

    @GetMapping("/reports/reconciliation")
    @ResponseBody
    Map<String, Object> reconciliation(@RequestParam(required = false) Instant from,
            @RequestParam(required = false) Instant to) {
        LocalDateTime start = from == null ? LocalDateTime.now().minusDays(30)
            : LocalDateTime.ofInstant(from, ZoneId.systemDefault());
        LocalDateTime end = to == null ? LocalDateTime.now()
            : LocalDateTime.ofInstant(to, ZoneId.systemDefault());
        List<PaymentTransaction> list = transactions
            .findByStatusAndPaidAtBetweenOrderByPaidAtDesc(PaymentStatus.RECONCILED,
                start.atZone(ZoneId.systemDefault()).toInstant(),
                end.atZone(ZoneId.systemDefault()).toInstant());
        Map<String, Object> result = new LinkedHashMap<String, Object>();
        result.put("from", start);
        result.put("to", end);
        result.put("count", list.size());
        result.put("amount", list.stream().map(PaymentTransaction::getAmount)
            .reduce(BigDecimal.ZERO, BigDecimal::add));
        result.put("transactions", list.stream().map(transaction -> {
            Map<String, Object> row = new LinkedHashMap<String, Object>();
            row.put("receiptNumber", transaction.getReceiptNumber());
            row.put("paymentType", transaction.getPaymentType().name());
            row.put("paymentMethod", transaction.getPaymentMethod().name());
            row.put("amount", transaction.getAmount());
            row.put("status", transaction.getStatus().name());
            row.put("externalReference", transaction.getExternalReference());
            row.put("paidAt", transaction.getPaidAt());
            row.put("sessionId", transaction.getParkingSession() == null
                ? null : transaction.getParkingSession().getId());
            row.put("subscriptionPaymentId", transaction.getSubscriptionPayment() == null
                ? null : transaction.getSubscriptionPayment().getId());
            row.put("collectedBy", transaction.getCollectedBy() == null
                ? null : transaction.getCollectedBy().getUsername());
            row.put("reconciledBy", transaction.getReconciledBy() == null
                ? null : transaction.getReconciledBy().getUsername());
            row.put("reconciledAt", transaction.getReconciledAt());
            row.put("reconciliationNote", transaction.getReconciliationNote());
            return row;
        }).toList());
        return result;
    }

    @GetMapping(value = "/reports/reconciliation.csv", produces = "text/csv; charset=UTF-8")
    @ResponseBody
    public ResponseEntity<byte[]> reconciliationCsv(@RequestParam(required = false) Instant from,
            @RequestParam(required = false) Instant to) {
        LocalDateTime start = from == null ? LocalDateTime.now().minusDays(30)
            : LocalDateTime.ofInstant(from, ZoneId.systemDefault());
        LocalDateTime end = to == null ? LocalDateTime.now()
            : LocalDateTime.ofInstant(to, ZoneId.systemDefault());
        List<PaymentTransaction> list = transactions
            .findByStatusAndPaidAtBetweenOrderByPaidAtDesc(PaymentStatus.RECONCILED,
                start.atZone(ZoneId.systemDefault()).toInstant(),
                end.atZone(ZoneId.systemDefault()).toInstant());

        StringBuilder csv = new StringBuilder(CsvUtils.UTF_8_BOM);
        csv.append("Số biên nhận,Loại phí,Phương thức thanh toán,Số tiền (VNĐ),Trạng thái,Mã tham chiếu,Thời gian thanh toán,Mã phiên gửi xe,Mã gói tháng,Thu ngân,Người đối soát,Thời gian đối soát,Ghi chú đối soát\r\n");

        DateTimeFormatter dtf = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss").withZone(ZoneId.systemDefault());
        BigDecimal totalAmount = BigDecimal.ZERO;

        for (PaymentTransaction tx : list) {
            BigDecimal amt = tx.getAmount() != null ? tx.getAmount() : BigDecimal.ZERO;
            totalAmount = totalAmount.add(amt);
            String collector = tx.getCollectedBy() != null ? (tx.getCollectedBy().getFullName() != null && !tx.getCollectedBy().getFullName().isBlank() ? tx.getCollectedBy().getFullName() : tx.getCollectedBy().getUsername()) : "";
            String reconciler = tx.getReconciledBy() != null ? (tx.getReconciledBy().getFullName() != null && !tx.getReconciledBy().getFullName().isBlank() ? tx.getReconciledBy().getFullName() : tx.getReconciledBy().getUsername()) : "";

            csv.append(CsvUtils.escape(tx.getReceiptNumber())).append(",")
               .append(CsvUtils.escape(tx.getPaymentType() != null ? tx.getPaymentType().getDisplayName() : "")).append(",")
               .append(CsvUtils.escape(tx.getPaymentMethod() != null ? tx.getPaymentMethod().getDisplayName() : "")).append(",")
               .append(amt.toPlainString()).append(",")
               .append(CsvUtils.escape(tx.getStatus() != null ? tx.getStatus().getDisplayName() : "")).append(",")
               .append(CsvUtils.escape(tx.getExternalReference())).append(",")
               .append(CsvUtils.escape(tx.getPaidAt() != null ? dtf.format(tx.getPaidAt()) : "")).append(",")
               .append(CsvUtils.escape(tx.getParkingSession() != null ? tx.getParkingSession().getId() : "")).append(",")
               .append(CsvUtils.escape(tx.getSubscriptionPayment() != null ? tx.getSubscriptionPayment().getId() : "")).append(",")
               .append(CsvUtils.escape(collector)).append(",")
               .append(CsvUtils.escape(reconciler)).append(",")
               .append(CsvUtils.escape(tx.getReconciledAt() != null ? dtf.format(tx.getReconciledAt()) : "")).append(",")
               .append(CsvUtils.escape(tx.getReconciliationNote())).append("\r\n");
        }

        csv.append("TỔNG CỘNG,,,")
           .append(totalAmount.toPlainString())
           .append(",,,,,,,,Tổng số giao dịch: ").append(list.size())
           .append("\r\n");

        byte[] bytes = csv.toString().getBytes(StandardCharsets.UTF_8);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"doi_soat_thanh_toan.csv\"")
                .header(HttpHeaders.CONTENT_TYPE, "text/csv; charset=UTF-8")
                .body(bytes);
    }

}

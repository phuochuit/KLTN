package vn.edu.parking.domain;

public enum PaymentStatus {
    PENDING("Chờ xác nhận"),
    PAID("Đã thu"),
    RECONCILED("Đã đối soát"),
    VOIDED("Đã hủy"),
    REFUNDED("Đã hoàn tiền");

    private final String displayName;
    PaymentStatus(String displayName) { this.displayName = displayName; }
    public String getDisplayName() { return displayName; }
}

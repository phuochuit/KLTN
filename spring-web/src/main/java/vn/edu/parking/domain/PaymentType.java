package vn.edu.parking.domain;

public enum PaymentType {
    PARKING_EXIT("Phí lượt gửi xe"),
    SUBSCRIPTION("Phí gói gửi xe");

    private final String displayName;
    PaymentType(String displayName) { this.displayName = displayName; }
    public String getDisplayName() { return displayName; }
}

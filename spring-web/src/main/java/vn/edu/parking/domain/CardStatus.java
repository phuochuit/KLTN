package vn.edu.parking.domain;

public enum CardStatus {
    ACTIVE("Hoạt động"), BLOCKED("Đã khóa"), EXPIRED("Hết hạn");

    private final String displayName;

    CardStatus(String displayName) { this.displayName = displayName; }
    public String getDisplayName() { return displayName; }
}

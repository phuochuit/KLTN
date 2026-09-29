package vn.edu.parking.domain;

public enum SessionStatus {
    OPEN("Trong bãi"), COMPLETED("Đã ra"), CANCELLED("Đã hủy");

    private final String displayName;

    SessionStatus(String displayName) { this.displayName = displayName; }
    public String getDisplayName() { return displayName; }
}

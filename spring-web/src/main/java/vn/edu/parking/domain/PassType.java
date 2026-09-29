package vn.edu.parking.domain;

public enum PassType {
    PER_VISIT("Vé lượt"),
    MONTHLY("Gói 30 ngày");

    private final String displayName;
    PassType(String displayName) { this.displayName = displayName; }
    public String getDisplayName() { return displayName; }
}

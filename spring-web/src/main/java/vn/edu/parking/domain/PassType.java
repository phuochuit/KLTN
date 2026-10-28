package vn.edu.parking.domain;

public enum PassType {
    /** Legacy rows only: no longer offered for new resident registrations. */
    PER_VISIT("Vé lượt"),
    DAILY("Gói theo ngày"),
    MONTHLY("Gói 30 ngày");

    private final String displayName;
    PassType(String displayName) { this.displayName = displayName; }
    public String getDisplayName() { return displayName; }
}

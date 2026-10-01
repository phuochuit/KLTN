package vn.edu.parking.domain;

public enum SlotType {
    RESIDENT_RESERVED("Cư dân đăng ký"),
    VISITOR_FLEXIBLE("Khách vãng lai");

    private final String displayName;

    SlotType(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}

package vn.edu.parking.domain;

public enum SlotStatusOverride {
    NORMAL("Bình thường"),
    BLOCKED("Cấm đỗ / Đang bảo trì");

    private final String displayName;

    SlotStatusOverride(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}

package vn.edu.parking.domain;

public enum FuelType {
    GASOLINE("Xăng/dầu"), ELECTRIC("Điện"), HYBRID("Hybrid"), HUMAN_POWERED("Sức người"), OTHER("Khác");

    private final String displayName;
    FuelType(String displayName) { this.displayName = displayName; }
    public String getDisplayName() { return displayName; }
}

package vn.edu.parking.domain;

public enum VehicleType {
    BICYCLE_ELECTRIC_BICYCLE("Xe đạp / xe đạp điện", false),
    MOTORBIKE("Xe máy / xe máy điện thông thường", false),
    LARGE_MOTORBIKE("Xe máy phân khối lớn", false),
    CAR("Ô tô 4-5 chỗ", true),
    CAR_6_7("Ô tô 6-7 chỗ", true),
    CAR_8_9("Ô tô 8-9 chỗ", true);

    private final String displayName;
    private final boolean car;

    VehicleType(String displayName, boolean car) {
        this.displayName = displayName;
        this.car = car;
    }
    public String getDisplayName() { return displayName; }
    public boolean isCar() { return car; }
    public boolean isTwoWheeler() { return !car; }
    public boolean isBicycleGroup() { return this == BICYCLE_ELECTRIC_BICYCLE; }
}

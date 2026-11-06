package vn.edu.parking.web.dto;

public class VehicleTypeDto {
    private Long id;
    private String code;
    private String displayName;
    private String category;
    private int capacityUnits;
    private boolean hasPricingRule;
    private boolean active;

    public VehicleTypeDto() {}

    public VehicleTypeDto(Long id, String code, String displayName, String category, int capacityUnits, boolean hasPricingRule, boolean active) {
        this.id = id;
        this.code = code;
        this.displayName = displayName;
        this.category = category;
        this.capacityUnits = capacityUnits;
        this.hasPricingRule = hasPricingRule;
        this.active = active;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }

    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }

    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }

    public int getCapacityUnits() { return capacityUnits; }
    public void setCapacityUnits(int capacityUnits) { this.capacityUnits = capacityUnits; }

    public boolean isHasPricingRule() { return hasPricingRule; }
    public void setHasPricingRule(boolean hasPricingRule) { this.hasPricingRule = hasPricingRule; }

    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
}

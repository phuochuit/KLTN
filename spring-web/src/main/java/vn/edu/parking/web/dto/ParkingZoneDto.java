package vn.edu.parking.web.dto;

import java.util.ArrayList;
import java.util.List;

public class ParkingZoneDto {
    private Long id;
    private Long floorId;
    private String floorName;
    private boolean isBasement;
    private String name;
    private String codePrefix;
    private Long vehicleTypeId;
    private String vehicleTypeCode;
    private String vehicleTypeName;
    private int capacityUnits;
    private String side;
    private int activeSlotCount;
    private List<String> occupiedSlotCodes = new ArrayList<>();
    private boolean active;

    public ParkingZoneDto() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getFloorId() { return floorId; }
    public void setFloorId(Long floorId) { this.floorId = floorId; }

    public String getFloorName() { return floorName; }
    public void setFloorName(String floorName) { this.floorName = floorName; }

    public boolean isBasement() { return isBasement; }
    public void setBasement(boolean basement) { isBasement = basement; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getCodePrefix() { return codePrefix; }
    public void setCodePrefix(String codePrefix) { this.codePrefix = codePrefix; }

    public Long getVehicleTypeId() { return vehicleTypeId; }
    public void setVehicleTypeId(Long vehicleTypeId) { this.vehicleTypeId = vehicleTypeId; }

    public String getVehicleTypeCode() { return vehicleTypeCode; }
    public void setVehicleTypeCode(String vehicleTypeCode) { this.vehicleTypeCode = vehicleTypeCode; }

    public String getVehicleTypeName() { return vehicleTypeName; }
    public void setVehicleTypeName(String vehicleTypeName) { this.vehicleTypeName = vehicleTypeName; }

    public int getCapacityUnits() { return capacityUnits; }
    public void setCapacityUnits(int capacityUnits) { this.capacityUnits = capacityUnits; }

    public String getSide() { return side; }
    public void setSide(String side) { this.side = side; }

    public int getActiveSlotCount() { return activeSlotCount; }
    public void setActiveSlotCount(int activeSlotCount) { this.activeSlotCount = activeSlotCount; }

    public List<String> getOccupiedSlotCodes() { return occupiedSlotCodes; }
    public void setOccupiedSlotCodes(List<String> occupiedSlotCodes) { this.occupiedSlotCodes = occupiedSlotCodes; }

    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
}

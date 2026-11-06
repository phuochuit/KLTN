package vn.edu.parking.web.dto;

import java.util.ArrayList;
import java.util.List;

public class ZoneConversionPreviewResponse {
    private Long zoneId;
    private String zoneName;
    private String oldVehicleTypeName;
    private String newVehicleTypeName;
    private int oldSlotCount;
    private int newSlotCount;
    private int capacityUnitsBefore;
    private int capacityUnitsAfter;
    private int remainderUnits;
    private boolean willLoseCapacity;
    private String warningMessage;
    private List<String> busySlotCodes = new ArrayList<>();
    private boolean canConvert;

    public ZoneConversionPreviewResponse() {}

    public Long getZoneId() { return zoneId; }
    public void setZoneId(Long zoneId) { this.zoneId = zoneId; }

    public String getZoneName() { return zoneName; }
    public void setZoneName(String zoneName) { this.zoneName = zoneName; }

    public String getOldVehicleTypeName() { return oldVehicleTypeName; }
    public void setOldVehicleTypeName(String oldVehicleTypeName) { this.oldVehicleTypeName = oldVehicleTypeName; }

    public String getNewVehicleTypeName() { return newVehicleTypeName; }
    public void setNewVehicleTypeName(String newVehicleTypeName) { this.newVehicleTypeName = newVehicleTypeName; }

    public int getOldSlotCount() { return oldSlotCount; }
    public void setOldSlotCount(int oldSlotCount) { this.oldSlotCount = oldSlotCount; }

    public int getNewSlotCount() { return newSlotCount; }
    public void setNewSlotCount(int newSlotCount) { this.newSlotCount = newSlotCount; }

    public int getCapacityUnitsBefore() { return capacityUnitsBefore; }
    public void setCapacityUnitsBefore(int capacityUnitsBefore) { this.capacityUnitsBefore = capacityUnitsBefore; }

    public int getCapacityUnitsAfter() { return capacityUnitsAfter; }
    public void setCapacityUnitsAfter(int capacityUnitsAfter) { this.capacityUnitsAfter = capacityUnitsAfter; }

    public int getRemainderUnits() { return remainderUnits; }
    public void setRemainderUnits(int remainderUnits) { this.remainderUnits = remainderUnits; }

    public boolean isWillLoseCapacity() { return willLoseCapacity; }
    public void setWillLoseCapacity(boolean willLoseCapacity) { this.willLoseCapacity = willLoseCapacity; }

    public String getWarningMessage() { return warningMessage; }
    public void setWarningMessage(String warningMessage) { this.warningMessage = warningMessage; }

    public List<String> getBusySlotCodes() { return busySlotCodes; }
    public void setBusySlotCodes(List<String> busySlotCodes) { this.busySlotCodes = busySlotCodes; }

    public boolean isCanConvert() { return canConvert; }
    public void setCanConvert(boolean canConvert) { this.canConvert = canConvert; }
}

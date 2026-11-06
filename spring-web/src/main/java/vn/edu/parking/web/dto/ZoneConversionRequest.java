package vn.edu.parking.web.dto;

public class ZoneConversionRequest {
    private Long targetVehicleTypeId;
    private boolean dryRun = false;
    private boolean confirmLoss = false;

    public ZoneConversionRequest() {}

    public Long getTargetVehicleTypeId() { return targetVehicleTypeId; }
    public void setTargetVehicleTypeId(Long targetVehicleTypeId) { this.targetVehicleTypeId = targetVehicleTypeId; }

    public boolean isDryRun() { return dryRun; }
    public void setDryRun(boolean dryRun) { this.dryRun = dryRun; }

    public boolean isConfirmLoss() { return confirmLoss; }
    public void setConfirmLoss(boolean confirmLoss) { this.confirmLoss = confirmLoss; }
}

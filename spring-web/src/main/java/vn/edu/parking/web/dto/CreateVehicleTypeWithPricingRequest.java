package vn.edu.parking.web.dto;

import vn.edu.parking.domain.VehicleType;
import java.math.BigDecimal;

public class CreateVehicleTypeWithPricingRequest {
    private String code;
    private String displayName;
    private VehicleType category;
    private int capacityUnits = 1;

    private BigDecimal basePrice;
    private BigDecimal nightPrice;
    private BigDecimal overnightFee;
    private BigDecimal dailyPassPrice;
    private BigDecimal monthlyPrice;
    private Integer baseHours;
    private Integer extraBlockHours;
    private BigDecimal extraBlockPrice;

    public CreateVehicleTypeWithPricingRequest() {}

    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }

    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }

    public VehicleType getCategory() { return category; }
    public void setCategory(VehicleType category) { this.category = category; }

    public int getCapacityUnits() { return capacityUnits; }
    public void setCapacityUnits(int capacityUnits) { this.capacityUnits = capacityUnits; }

    public BigDecimal getBasePrice() { return basePrice; }
    public void setBasePrice(BigDecimal basePrice) { this.basePrice = basePrice; }

    public BigDecimal getNightPrice() { return nightPrice; }
    public void setNightPrice(BigDecimal nightPrice) { this.nightPrice = nightPrice; }

    public BigDecimal getOvernightFee() { return overnightFee; }
    public void setOvernightFee(BigDecimal overnightFee) { this.overnightFee = overnightFee; }

    public BigDecimal getDailyPassPrice() { return dailyPassPrice; }
    public void setDailyPassPrice(BigDecimal dailyPassPrice) { this.dailyPassPrice = dailyPassPrice; }

    public BigDecimal getMonthlyPrice() { return monthlyPrice; }
    public void setMonthlyPrice(BigDecimal monthlyPrice) { this.monthlyPrice = monthlyPrice; }

    public Integer getBaseHours() { return baseHours; }
    public void setBaseHours(Integer baseHours) { this.baseHours = baseHours; }

    public Integer getExtraBlockHours() { return extraBlockHours; }
    public void setExtraBlockHours(Integer extraBlockHours) { this.extraBlockHours = extraBlockHours; }

    public BigDecimal getExtraBlockPrice() { return extraBlockPrice; }
    public void setExtraBlockPrice(BigDecimal extraBlockPrice) { this.extraBlockPrice = extraBlockPrice; }
}

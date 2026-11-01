package vn.edu.parking.domain;

import jakarta.persistence.*;
import java.math.BigDecimal;

@Entity
@Table(name = "pricing_rules")
public class PricingRule {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "vehicle_type", nullable = true, length = 50)
    private VehicleType vehicleType;

    @OneToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "vehicle_type_id", unique = true)
    private VehicleTypeEntity vehicleTypeRef;

    @Column(nullable = false, precision = 12, scale = 0)
    private BigDecimal basePrice;
    @Column(precision = 12, scale = 0)
    private BigDecimal nightPrice;
    @Column(nullable = false, precision = 12, scale = 0)
    private BigDecimal overnightFee;
    @Column(precision = 12, scale = 0)
    private BigDecimal monthlyPrice;
    @Column(name = "daily_pass_price", precision = 12, scale = 0)
    private BigDecimal dailyPassPrice;
    private Integer baseHours;
    private Integer extraBlockHours;
    @Column(precision = 12, scale = 0)
    private BigDecimal extraBlockPrice;

    public String getDisplayTitle() {
        if (vehicleTypeRef != null && vehicleTypeRef.getDisplayName() != null && !vehicleTypeRef.getDisplayName().isBlank()) {
            return vehicleTypeRef.getDisplayName();
        }
        return vehicleType != null ? vehicleType.getDisplayName() : "Chưa xác định";
    }

    public boolean isCarCategory() {
        if (vehicleTypeRef != null && vehicleTypeRef.getCategory() != null) {
            return vehicleTypeRef.getCategory().isCar();
        }
        return vehicleType != null && vehicleType.isCar();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public VehicleType getVehicleType() { return vehicleType; }
    public void setVehicleType(VehicleType vehicleType) { this.vehicleType = vehicleType; }

    public VehicleTypeEntity getVehicleTypeRef() { return vehicleTypeRef; }
    public void setVehicleTypeRef(VehicleTypeEntity vehicleTypeRef) { this.vehicleTypeRef = vehicleTypeRef; }

    public BigDecimal getBasePrice() { return basePrice; }
    public void setBasePrice(BigDecimal basePrice) { this.basePrice = basePrice; }
    public BigDecimal getNightPrice() { return nightPrice == null ? getBasePrice() : nightPrice; }
    public void setNightPrice(BigDecimal nightPrice) { this.nightPrice = nightPrice; }
    public BigDecimal getOvernightFee() { return overnightFee; }
    public void setOvernightFee(BigDecimal overnightFee) { this.overnightFee = overnightFee; }
    public BigDecimal getMonthlyPrice() { return monthlyPrice == null ? BigDecimal.ZERO : monthlyPrice; }
    public void setMonthlyPrice(BigDecimal monthlyPrice) { this.monthlyPrice = monthlyPrice; }
    public BigDecimal getDailyPassPrice() { return dailyPassPrice == null ? BigDecimal.ZERO : dailyPassPrice; }
    public void setDailyPassPrice(BigDecimal dailyPassPrice) { this.dailyPassPrice = dailyPassPrice; }
    public int getBaseHours() { return baseHours == null ? 0 : baseHours; }
    public void setBaseHours(Integer baseHours) { this.baseHours = baseHours; }
    public int getExtraBlockHours() { return extraBlockHours == null ? 0 : extraBlockHours; }
    public void setExtraBlockHours(Integer extraBlockHours) { this.extraBlockHours = extraBlockHours; }
    public BigDecimal getExtraBlockPrice() { return extraBlockPrice == null ? BigDecimal.ZERO : extraBlockPrice; }
    public void setExtraBlockPrice(BigDecimal extraBlockPrice) { this.extraBlockPrice = extraBlockPrice; }
}

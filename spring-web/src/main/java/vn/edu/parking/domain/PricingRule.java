package vn.edu.parking.domain;

import jakarta.persistence.*;
import java.math.BigDecimal;

@Entity
@Table(name = "pricing_rules")
public class PricingRule {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, unique = true, length = 50, columnDefinition = "varchar(50)")
    private VehicleType vehicleType;
    @Column(nullable = false, precision = 12, scale = 0)
    private BigDecimal basePrice;
    @Column(precision = 12, scale = 0)
    private BigDecimal nightPrice;
    @Column(nullable = false, precision = 12, scale = 0)
    private BigDecimal overnightFee;
    @Column(precision = 12, scale = 0)
    private BigDecimal monthlyPrice;
    private Integer baseHours;
    private Integer extraBlockHours;
    @Column(precision = 12, scale = 0)
    private BigDecimal extraBlockPrice;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public VehicleType getVehicleType() { return vehicleType; }
    public void setVehicleType(VehicleType vehicleType) { this.vehicleType = vehicleType; }
    public BigDecimal getBasePrice() { return basePrice; }
    public void setBasePrice(BigDecimal basePrice) { this.basePrice = basePrice; }
    public BigDecimal getNightPrice() { return nightPrice == null ? getBasePrice() : nightPrice; }
    public void setNightPrice(BigDecimal nightPrice) { this.nightPrice = nightPrice; }
    public BigDecimal getOvernightFee() { return overnightFee; }
    public void setOvernightFee(BigDecimal overnightFee) { this.overnightFee = overnightFee; }
    public BigDecimal getMonthlyPrice() { return monthlyPrice == null ? BigDecimal.ZERO : monthlyPrice; }
    public void setMonthlyPrice(BigDecimal monthlyPrice) { this.monthlyPrice = monthlyPrice; }
    public int getBaseHours() { return baseHours == null ? 0 : baseHours; }
    public void setBaseHours(Integer baseHours) { this.baseHours = baseHours; }
    public int getExtraBlockHours() { return extraBlockHours == null ? 0 : extraBlockHours; }
    public void setExtraBlockHours(Integer extraBlockHours) { this.extraBlockHours = extraBlockHours; }
    public BigDecimal getExtraBlockPrice() { return extraBlockPrice == null ? BigDecimal.ZERO : extraBlockPrice; }
    public void setExtraBlockPrice(BigDecimal extraBlockPrice) { this.extraBlockPrice = extraBlockPrice; }
}

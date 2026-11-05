package vn.edu.parking.domain;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "parking_sessions", uniqueConstraints = @UniqueConstraint(
    name = "uk_parking_sessions_open_plate", columnNames = "open_plate"))
public class ParkingSession {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.EAGER)
    private Vehicle vehicle;
    @ManyToOne(fetch = FetchType.EAGER)
    private ParkingCard parkingCard;
    @Column(nullable = false)
    private String entryPlate;
    @Column(name = "open_plate", length = 20)
    private String openPlate;
    private String exitPlate;
    @Column(nullable = false)
    private LocalDateTime entryTime;
    private LocalDateTime exitTime;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SessionStatus status = SessionStatus.OPEN;
    @Enumerated(EnumType.STRING)
    @Column(length = 50, columnDefinition = "varchar(50)")
    private VehicleType detectedVehicleType = VehicleType.MOTORBIKE;
    @Column(nullable = false, precision = 12, scale = 0)
    private BigDecimal fee = BigDecimal.ZERO;
    @Column(nullable = false, columnDefinition = "boolean default false")
    private boolean manualOverride;
    @ManyToOne(fetch = FetchType.EAGER)
    private FamilyMember entryMember;
    @ManyToOne(fetch = FetchType.EAGER)
    private FamilyMember exitMember;
    @Column(nullable = false, columnDefinition = "boolean default false")
    private boolean entryFaceVerified;
    private Double entryFaceSimilarity;
    @Column(nullable = false, columnDefinition = "boolean default false")
    private boolean exitFaceVerified;
    private Double exitFaceSimilarity;
    @Column(length = 500)
    private String entryFaceImagePath;
    @Column(name = "prepaid_package_applied")
    private Boolean prepaidPackageApplied;
    @Column(name = "price_base_snapshot", precision = 12, scale = 0)
    private BigDecimal priceBaseSnapshot;
    @Column(name = "price_night_snapshot", precision = 12, scale = 0)
    private BigDecimal priceNightSnapshot;
    @Column(name = "price_overnight_snapshot", precision = 12, scale = 0)
    private BigDecimal priceOvernightSnapshot;
    @Column(name = "price_extra_block_snapshot", precision = 12, scale = 0)
    private BigDecimal priceExtraBlockSnapshot;
    @Column(name = "base_hours_snapshot")
    private Integer baseHoursSnapshot;
    @Column(name = "extra_block_hours_snapshot")
    private Integer extraBlockHoursSnapshot;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Vehicle getVehicle() { return vehicle; }
    public void setVehicle(Vehicle vehicle) { this.vehicle = vehicle; }
    public ParkingCard getParkingCard() { return parkingCard; }
    public void setParkingCard(ParkingCard parkingCard) { this.parkingCard = parkingCard; }
    public String getEntryPlate() { return entryPlate; }
    public void setEntryPlate(String entryPlate) { this.entryPlate = entryPlate; }
    public String getOpenPlate() { return openPlate; }
    public void setOpenPlate(String openPlate) { this.openPlate = openPlate; }
    public String getExitPlate() { return exitPlate; }
    public void setExitPlate(String exitPlate) { this.exitPlate = exitPlate; }
    public LocalDateTime getEntryTime() { return entryTime; }
    public void setEntryTime(LocalDateTime entryTime) { this.entryTime = entryTime; }
    public LocalDateTime getExitTime() { return exitTime; }
    public void setExitTime(LocalDateTime exitTime) { this.exitTime = exitTime; }
    public SessionStatus getStatus() { return status; }
    public void setStatus(SessionStatus status) { this.status = status; }
    public VehicleType getDetectedVehicleType() { return detectedVehicleType == null ? VehicleType.MOTORBIKE : detectedVehicleType; }
    public void setDetectedVehicleType(VehicleType detectedVehicleType) { this.detectedVehicleType = detectedVehicleType; }
    public BigDecimal getFee() { return fee; }
    public void setFee(BigDecimal fee) { this.fee = fee; }
    public boolean isManualOverride() { return manualOverride; }
    public void setManualOverride(boolean manualOverride) { this.manualOverride = manualOverride; }
    public FamilyMember getEntryMember() { return entryMember; }
    public void setEntryMember(FamilyMember entryMember) { this.entryMember = entryMember; }
    public FamilyMember getExitMember() { return exitMember; }
    public void setExitMember(FamilyMember exitMember) { this.exitMember = exitMember; }
    public boolean isEntryFaceVerified() { return entryFaceVerified; }
    public void setEntryFaceVerified(boolean value) { this.entryFaceVerified = value; }
    public Double getEntryFaceSimilarity() { return entryFaceSimilarity; }
    public void setEntryFaceSimilarity(Double value) { this.entryFaceSimilarity = value; }
    public boolean isExitFaceVerified() { return exitFaceVerified; }
    public void setExitFaceVerified(boolean value) { this.exitFaceVerified = value; }
    public Double getExitFaceSimilarity() { return exitFaceSimilarity; }
    public void setExitFaceSimilarity(Double value) { this.exitFaceSimilarity = value; }
    public String getEntryFaceImagePath() { return entryFaceImagePath; }
    public void setEntryFaceImagePath(String value) { this.entryFaceImagePath = value; }
    public Boolean getPrepaidPackageApplied() { return prepaidPackageApplied; }
    public void setPrepaidPackageApplied(Boolean value) { this.prepaidPackageApplied = value; }
    public BigDecimal getPriceBaseSnapshot() { return priceBaseSnapshot; }
    public void setPriceBaseSnapshot(BigDecimal value) { this.priceBaseSnapshot = value; }
    public BigDecimal getPriceNightSnapshot() { return priceNightSnapshot; }
    public void setPriceNightSnapshot(BigDecimal value) { this.priceNightSnapshot = value; }
    public BigDecimal getPriceOvernightSnapshot() { return priceOvernightSnapshot; }
    public void setPriceOvernightSnapshot(BigDecimal value) { this.priceOvernightSnapshot = value; }
    public BigDecimal getPriceExtraBlockSnapshot() { return priceExtraBlockSnapshot; }
    public void setPriceExtraBlockSnapshot(BigDecimal value) { this.priceExtraBlockSnapshot = value; }
    public Integer getBaseHoursSnapshot() { return baseHoursSnapshot; }
    public void setBaseHoursSnapshot(Integer value) { this.baseHoursSnapshot = value; }
    public Integer getExtraBlockHoursSnapshot() { return extraBlockHoursSnapshot; }
    public void setExtraBlockHoursSnapshot(Integer value) { this.extraBlockHoursSnapshot = value; }
}

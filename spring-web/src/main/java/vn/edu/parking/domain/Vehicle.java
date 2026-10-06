package vn.edu.parking.domain;

import jakarta.persistence.*;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.stream.Collectors;
import java.time.LocalDateTime;

@Entity
@Table(name = "vehicles", uniqueConstraints = @UniqueConstraint(columnNames = "plate_number"))
public class Vehicle {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "plate_number", nullable = false, length = 20)
    private String plateNumber;
    @Column(nullable = false)
    private String ownerName;
    private String ownerPhone;
    private String apartmentNumber;
    @ManyToOne(fetch = FetchType.EAGER)
    private Household household;
    @ManyToOne(fetch = FetchType.EAGER)
    private FamilyMember registeredOwner;
    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(name = "vehicle_authorized_members",
        joinColumns = @JoinColumn(name = "vehicle_id"),
        inverseJoinColumns = @JoinColumn(name = "family_member_id"))
    private Set<FamilyMember> authorizedMembers = new LinkedHashSet<>();
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50, columnDefinition = "varchar(50)")
    private VehicleType vehicleType = VehicleType.MOTORBIKE;
    private String registrationNumber;
    private String brand;
    private String modelName;
    private String color;
    private Integer manufactureYear;
    private String chassisNumber;
    private String engineNumber;
    private Integer seatCount;
    private Integer cylinderCapacityCc;
    @Enumerated(EnumType.STRING)
    private FuelType fuelType = FuelType.GASOLINE;
    @Column(length = 500)
    private String registrationImagePath;
    private boolean active = true;
    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @PrePersist
    void initializeCreatedAt() {
        if (createdAt == null) createdAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getPlateNumber() { return plateNumber; }
    public void setPlateNumber(String plateNumber) { this.plateNumber = plateNumber; }
    public String getOwnerName() { return ownerName; }
    public void setOwnerName(String ownerName) { this.ownerName = ownerName; }
    public String getOwnerPhone() { return ownerPhone; }
    public void setOwnerPhone(String ownerPhone) { this.ownerPhone = ownerPhone; }
    public String getApartmentNumber() { return apartmentNumber; }
    public void setApartmentNumber(String apartmentNumber) { this.apartmentNumber = apartmentNumber; }
    public Household getHousehold() { return household; }
    public void setHousehold(Household household) { this.household = household; }
    public FamilyMember getRegisteredOwner() { return registeredOwner; }
    public void setRegisteredOwner(FamilyMember registeredOwner) { this.registeredOwner = registeredOwner; }
    public Set<FamilyMember> getAuthorizedMembers() { return authorizedMembers; }
    public void setAuthorizedMembers(Set<FamilyMember> authorizedMembers) {
        this.authorizedMembers = authorizedMembers == null ? new LinkedHashSet<>() : authorizedMembers;
    }
    public VehicleType getVehicleType() { return vehicleType; }
    public void setVehicleType(VehicleType vehicleType) { this.vehicleType = vehicleType; }
    public String getRegistrationNumber() { return registrationNumber; }
    public void setRegistrationNumber(String registrationNumber) { this.registrationNumber = registrationNumber; }
    public String getBrand() { return brand; }
    public void setBrand(String brand) { this.brand = brand; }
    public String getModelName() { return modelName; }
    public void setModelName(String modelName) { this.modelName = modelName; }
    public String getColor() { return color; }
    public void setColor(String color) { this.color = color; }
    public Integer getManufactureYear() { return manufactureYear; }
    public void setManufactureYear(Integer manufactureYear) { this.manufactureYear = manufactureYear; }
    public String getChassisNumber() { return chassisNumber; }
    public void setChassisNumber(String chassisNumber) { this.chassisNumber = chassisNumber; }
    public String getEngineNumber() { return engineNumber; }
    public void setEngineNumber(String engineNumber) { this.engineNumber = engineNumber; }
    public Integer getSeatCount() { return seatCount; }
    public void setSeatCount(Integer seatCount) { this.seatCount = seatCount; }
    public Integer getCylinderCapacityCc() { return cylinderCapacityCc; }
    public void setCylinderCapacityCc(Integer cylinderCapacityCc) { this.cylinderCapacityCc = cylinderCapacityCc; }
    public FuelType getFuelType() { return fuelType == null ? FuelType.OTHER : fuelType; }
    public void setFuelType(FuelType fuelType) { this.fuelType = fuelType; }
    public String getRegistrationImagePath() { return registrationImagePath; }
    public void setRegistrationImagePath(String registrationImagePath) { this.registrationImagePath = registrationImagePath; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public String getEffectiveOwnerName() {
        return registeredOwner == null ? ownerName : registeredOwner.getFullName();
    }
    public String getEffectiveApartmentNumber() {
        return household == null ? apartmentNumber : household.getApartmentNumber();
    }
    public String getAuthorizedMemberNames() {
        return authorizedMembers.stream().filter(FamilyMember::isActive)
            .map(FamilyMember::getFullName).collect(Collectors.joining(", "));
    }
}

package vn.edu.parking.domain;

import jakarta.persistence.*;

@Entity
@Table(name = "households", uniqueConstraints = @UniqueConstraint(columnNames = "household_code"))
public class Household {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "household_code", nullable = false, length = 40)
    private String householdCode;
    @Column(nullable = false, length = 40)
    private String apartmentNumber;
    private String buildingName;
    private String contactPhone;
    private Integer maxTwoWheelers;
    private Integer maxCars;
    private boolean active = true;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getHouseholdCode() { return householdCode; }
    public void setHouseholdCode(String householdCode) { this.householdCode = householdCode; }
    public String getApartmentNumber() { return apartmentNumber; }
    public void setApartmentNumber(String apartmentNumber) { this.apartmentNumber = apartmentNumber; }
    public String getBuildingName() { return buildingName; }
    public void setBuildingName(String buildingName) { this.buildingName = buildingName; }
    public String getContactPhone() { return contactPhone; }
    public void setContactPhone(String contactPhone) { this.contactPhone = contactPhone; }
    public Integer getMaxTwoWheelers() { return maxTwoWheelers; }
    public void setMaxTwoWheelers(Integer maxTwoWheelers) { this.maxTwoWheelers = maxTwoWheelers; }
    public Integer getMaxCars() { return maxCars; }
    public void setMaxCars(Integer maxCars) { this.maxCars = maxCars; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
}

package vn.edu.parking.domain;

import jakarta.persistence.*;

@Entity
@Table(name = "vehicle_types", uniqueConstraints = @UniqueConstraint(name = "uk_vehicle_type_code", columnNames = "code"))
public class VehicleTypeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 40)
    private String code;

    @Column(name = "display_name", nullable = false, length = 100)
    private String displayName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private VehicleType category;

    @Column(name = "capacity_units", nullable = false)
    private int capacityUnits = 1;

    @Column(nullable = false)
    private boolean active = true;

    public VehicleTypeEntity() {}

    public VehicleTypeEntity(String code, String displayName, VehicleType category, int capacityUnits) {
        this.code = code;
        this.displayName = displayName;
        this.category = category;
        this.capacityUnits = capacityUnits;
        this.active = true;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }

    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }

    public VehicleType getCategory() { return category; }
    public void setCategory(VehicleType category) { this.category = category; }

    public int getCapacityUnits() { return capacityUnits; }
    public void setCapacityUnits(int capacityUnits) { this.capacityUnits = capacityUnits; }

    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
}

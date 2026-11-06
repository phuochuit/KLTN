package vn.edu.parking.domain;

import jakarta.persistence.*;

@Entity
@Table(name = "parking_zones", uniqueConstraints = @UniqueConstraint(name = "uk_parking_zone_floor_name", columnNames = {"floor_id", "name"}))
public class ParkingZone {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "floor_id", nullable = false)
    private ParkingFloor floor;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(name = "code_prefix", nullable = false, length = 16)
    private String codePrefix;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "vehicle_type_id")
    private VehicleTypeEntity vehicleType;

    @Column(name = "capacity_units", nullable = false)
    private int capacityUnits;

    @Column(length = 32)
    private String side;

    @Column(nullable = false)
    private boolean active = true;

    public ParkingZone() {}

    public ParkingZone(ParkingFloor floor, String name, String codePrefix, VehicleTypeEntity vehicleType, int capacityUnits, String side) {
        this.floor = floor;
        this.name = name;
        this.codePrefix = codePrefix;
        this.vehicleType = vehicleType;
        this.capacityUnits = capacityUnits;
        this.side = side;
        this.active = true;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public ParkingFloor getFloor() { return floor; }
    public void setFloor(ParkingFloor floor) { this.floor = floor; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getCodePrefix() { return codePrefix; }
    public void setCodePrefix(String codePrefix) { this.codePrefix = codePrefix; }

    public VehicleTypeEntity getVehicleType() { return vehicleType; }
    public void setVehicleType(VehicleTypeEntity vehicleType) { this.vehicleType = vehicleType; }

    public int getCapacityUnits() { return capacityUnits; }
    public void setCapacityUnits(int capacityUnits) { this.capacityUnits = capacityUnits; }

    public String getSide() { return side; }
    public void setSide(String side) { this.side = side; }

    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
}

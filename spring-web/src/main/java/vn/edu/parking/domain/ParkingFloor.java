package vn.edu.parking.domain;

import jakarta.persistence.*;

@Entity
@Table(name = "parking_floors", uniqueConstraints = @UniqueConstraint(name = "uk_parking_floor_name", columnNames = "name"))
public class ParkingFloor {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(name = "is_basement", nullable = false)
    private boolean isBasement;

    @Column(name = "display_order", nullable = false)
    private int displayOrder = 0;

    @Column(name = "max_capacity_units", nullable = false)
    private int maxCapacityUnits = 500;

    @Column(nullable = false)
    private boolean active = true;

    public ParkingFloor() {}

    public ParkingFloor(String name, boolean isBasement, int displayOrder, int maxCapacityUnits) {
        this.name = name;
        this.isBasement = isBasement;
        this.displayOrder = displayOrder;
        this.maxCapacityUnits = maxCapacityUnits;
        this.active = true;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public boolean isBasement() { return isBasement; }
    public void setBasement(boolean basement) { isBasement = basement; }

    public int getDisplayOrder() { return displayOrder; }
    public void setDisplayOrder(int displayOrder) { this.displayOrder = displayOrder; }

    public int getMaxCapacityUnits() { return maxCapacityUnits; }
    public void setMaxCapacityUnits(int maxCapacityUnits) { this.maxCapacityUnits = maxCapacityUnits; }

    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
}

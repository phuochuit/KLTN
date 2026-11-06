package vn.edu.parking.domain;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "slot_conversion_logs")
public class SlotConversionLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "zone_id", nullable = false)
    private ParkingZone zone;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "old_vehicle_type_id")
    private VehicleTypeEntity oldVehicleType;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "new_vehicle_type_id", nullable = false)
    private VehicleTypeEntity newVehicleType;

    @Column(name = "old_slot_count", nullable = false)
    private int oldSlotCount;

    @Column(name = "new_slot_count", nullable = false)
    private int newSlotCount;

    @Column(name = "capacity_units_before", nullable = false)
    private int capacityUnitsBefore;

    @Column(name = "capacity_units_after", nullable = false)
    private int capacityUnitsAfter;

    @Column(name = "deactivated_count", nullable = false)
    private int deactivatedCount;

    @Column(name = "created_count", nullable = false)
    private int createdCount;

    @Column(name = "actor_username", length = 80)
    private String actorUsername;

    @Column(name = "converted_at", nullable = false)
    private Instant convertedAt = Instant.now();

    @Column(length = 400)
    private String note;

    public SlotConversionLog() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public ParkingZone getZone() { return zone; }
    public void setZone(ParkingZone zone) { this.zone = zone; }

    public VehicleTypeEntity getOldVehicleType() { return oldVehicleType; }
    public void setOldVehicleType(VehicleTypeEntity oldVehicleType) { this.oldVehicleType = oldVehicleType; }

    public VehicleTypeEntity getNewVehicleType() { return newVehicleType; }
    public void setNewVehicleType(VehicleTypeEntity newVehicleType) { this.newVehicleType = newVehicleType; }

    public int getOldSlotCount() { return oldSlotCount; }
    public void setOldSlotCount(int oldSlotCount) { this.oldSlotCount = oldSlotCount; }

    public int getNewSlotCount() { return newSlotCount; }
    public void setNewSlotCount(int newSlotCount) { this.newSlotCount = newSlotCount; }

    public int getCapacityUnitsBefore() { return capacityUnitsBefore; }
    public void setCapacityUnitsBefore(int capacityUnitsBefore) { this.capacityUnitsBefore = capacityUnitsBefore; }

    public int getCapacityUnitsAfter() { return capacityUnitsAfter; }
    public void setCapacityUnitsAfter(int capacityUnitsAfter) { this.capacityUnitsAfter = capacityUnitsAfter; }

    public int getDeactivatedCount() { return deactivatedCount; }
    public void setDeactivatedCount(int deactivatedCount) { this.deactivatedCount = deactivatedCount; }

    public int getCreatedCount() { return createdCount; }
    public void setCreatedCount(int createdCount) { this.createdCount = createdCount; }

    public String getActorUsername() { return actorUsername; }
    public void setActorUsername(String actorUsername) { this.actorUsername = actorUsername; }

    public Instant getConvertedAt() { return convertedAt; }
    public void setConvertedAt(Instant convertedAt) { this.convertedAt = convertedAt; }

    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }
}

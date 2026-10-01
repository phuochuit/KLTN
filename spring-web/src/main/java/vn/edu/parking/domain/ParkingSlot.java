package vn.edu.parking.domain;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "parking_slots", uniqueConstraints = @UniqueConstraint(columnNames = "slot_code"))
public class ParkingSlot {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "slot_code", nullable = false, length = 20)
    private String slotCode;

    @Column(length = 50)
    private String floor = "Tầng hầm B1";

    @Column(length = 50)
    private String zoneName = "Khu A";

    @Enumerated(EnumType.STRING)
    @Column(length = 30)
    private SlotType slotType = SlotType.RESIDENT_RESERVED;

    @Enumerated(EnumType.STRING)
    @Column(length = 30)
    private VehicleType allowedVehicleType = VehicleType.CAR;

    @Enumerated(EnumType.STRING)
    @Column(length = 30)
    private SlotStatusOverride statusOverride = SlotStatusOverride.NORMAL;

    @ManyToOne(fetch = FetchType.EAGER)
    private Vehicle assignedVehicle;

    @ManyToOne(fetch = FetchType.EAGER)
    private ParkingSession currentSession;

    @Column(length = 20)
    private String borrowedPlate;

    private LocalDateTime borrowedUntil;

    @Column(length = 255)
    private String borrowNotes;

    private boolean active = true;

    public boolean isOverdue(LocalDateTime now) {
        if (borrowedUntil == null) return false;
        LocalDateTime checkTime = (now != null) ? now : LocalDateTime.now();
        return checkTime.isAfter(borrowedUntil) && currentSession != null && currentSession.getStatus() == SessionStatus.OPEN;
    }

    public boolean isCurrentlyBorrowed() {
        return borrowedPlate != null && !borrowedPlate.isBlank() && currentSession != null
                && currentSession.getEntryPlate() != null
                && currentSession.getEntryPlate().equalsIgnoreCase(borrowedPlate.trim());
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getSlotCode() { return slotCode; }
    public void setSlotCode(String slotCode) { this.slotCode = slotCode; }

    public String getFloor() { return floor; }
    public void setFloor(String floor) { this.floor = floor; }

    public String getZoneName() { return zoneName; }
    public void setZoneName(String zoneName) { this.zoneName = zoneName; }

    public SlotType getSlotType() { return slotType; }
    public void setSlotType(SlotType slotType) { this.slotType = slotType; }

    public VehicleType getAllowedVehicleType() { return allowedVehicleType; }
    public void setAllowedVehicleType(VehicleType allowedVehicleType) { this.allowedVehicleType = allowedVehicleType; }

    public SlotStatusOverride getStatusOverride() { return statusOverride; }
    public void setStatusOverride(SlotStatusOverride statusOverride) { this.statusOverride = statusOverride; }

    public Vehicle getAssignedVehicle() { return assignedVehicle; }
    public void setAssignedVehicle(Vehicle assignedVehicle) { this.assignedVehicle = assignedVehicle; }

    public ParkingSession getCurrentSession() { return currentSession; }
    public void setCurrentSession(ParkingSession currentSession) { this.currentSession = currentSession; }

    public String getBorrowedPlate() { return borrowedPlate; }
    public void setBorrowedPlate(String borrowedPlate) { this.borrowedPlate = borrowedPlate; }

    public LocalDateTime getBorrowedUntil() { return borrowedUntil; }
    public void setBorrowedUntil(LocalDateTime borrowedUntil) { this.borrowedUntil = borrowedUntil; }

    public String getBorrowNotes() { return borrowNotes; }
    public void setBorrowNotes(String borrowNotes) { this.borrowNotes = borrowNotes; }

    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
}

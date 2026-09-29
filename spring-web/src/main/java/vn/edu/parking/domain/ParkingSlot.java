package vn.edu.parking.domain;

import jakarta.persistence.*;

@Entity
@Table(name = "parking_slots", uniqueConstraints = @UniqueConstraint(columnNames = "slot_code"))
public class ParkingSlot {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "slot_code", nullable = false, length = 20)
    private String slotCode;
    private String zoneName;
    @ManyToOne(fetch = FetchType.EAGER)
    private Vehicle assignedVehicle;
    @ManyToOne(fetch = FetchType.EAGER)
    private ParkingSession currentSession;
    private boolean active = true;

    public Long getId() { return id; }
    public String getSlotCode() { return slotCode; }
    public void setSlotCode(String slotCode) { this.slotCode = slotCode; }
    public String getZoneName() { return zoneName; }
    public void setZoneName(String zoneName) { this.zoneName = zoneName; }
    public Vehicle getAssignedVehicle() { return assignedVehicle; }
    public void setAssignedVehicle(Vehicle assignedVehicle) { this.assignedVehicle = assignedVehicle; }
    public ParkingSession getCurrentSession() { return currentSession; }
    public void setCurrentSession(ParkingSession currentSession) { this.currentSession = currentSession; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
}

package vn.edu.parking.domain;

import jakarta.persistence.*;

@Entity
@Table(name = "parking_policy")
public class ParkingPolicy {
    @Id
    private Long id = 1L;
    @Column(nullable = false)
    private int defaultMaxTwoWheelers = 2;
    @Column(nullable = false)
    private int defaultMaxCars = 1;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public int getDefaultMaxTwoWheelers() { return defaultMaxTwoWheelers; }
    public void setDefaultMaxTwoWheelers(int value) { this.defaultMaxTwoWheelers = value; }
    public int getDefaultMaxCars() { return defaultMaxCars; }
    public void setDefaultMaxCars(int value) { this.defaultMaxCars = value; }
}

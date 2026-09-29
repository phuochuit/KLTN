package vn.edu.parking.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import vn.edu.parking.domain.ParkingPolicy;

public interface ParkingPolicyRepository extends JpaRepository<ParkingPolicy, Long> { }

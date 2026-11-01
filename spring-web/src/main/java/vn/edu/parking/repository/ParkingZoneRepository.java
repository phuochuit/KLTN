package vn.edu.parking.repository;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import vn.edu.parking.domain.ParkingZone;

import java.util.List;
import java.util.Optional;

public interface ParkingZoneRepository extends JpaRepository<ParkingZone, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT z FROM ParkingZone z WHERE z.id = :id")
    Optional<ParkingZone> lockById(@Param("id") Long id);

    Optional<ParkingZone> findByFloor_IdAndNameIgnoreCase(Long floorId, String name);
    Optional<ParkingZone> findByNameIgnoreCase(String name);
    List<ParkingZone> findByFloor_IdAndActiveTrueOrderByNameAsc(Long floorId);
    List<ParkingZone> findByActiveTrueOrderByNameAsc();
    List<ParkingZone> findAllByOrderByNameAsc();
    boolean existsByFloor_IdAndNameIgnoreCase(Long floorId, String name);
}

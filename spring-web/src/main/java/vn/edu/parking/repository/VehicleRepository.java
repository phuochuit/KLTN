package vn.edu.parking.repository;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import vn.edu.parking.domain.Vehicle;
import java.util.List;
import java.util.Optional;
import java.time.LocalDateTime;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface VehicleRepository extends JpaRepository<Vehicle, Long> {
    Optional<Vehicle> findByPlateNumberIgnoreCase(String plateNumber);

    @Query(value = """
            select distinct v from Vehicle v
            left join v.household h
            left join v.registeredOwner owner
            where lower(v.plateNumber) like :searchPattern escape '!'
               or lower(v.ownerName) like :searchPattern escape '!'
               or lower(owner.fullName) like :searchPattern escape '!'
               or lower(h.householdCode) like :searchPattern escape '!'
               or lower(h.apartmentNumber) like :searchPattern escape '!'
            """,
            countQuery = """
            select count(distinct v) from Vehicle v
            left join v.household h
            left join v.registeredOwner owner
            where lower(v.plateNumber) like :searchPattern escape '!'
               or lower(v.ownerName) like :searchPattern escape '!'
               or lower(owner.fullName) like :searchPattern escape '!'
               or lower(h.householdCode) like :searchPattern escape '!'
               or lower(h.apartmentNumber) like :searchPattern escape '!'
            """)
    Page<Vehicle> searchManagement(@Param("searchPattern") String searchPattern, Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select v from Vehicle v where v.id = :id")
    Optional<Vehicle> findByIdForUpdate(@Param("id") Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select v from Vehicle v where lower(v.plateNumber) = lower(:plate)")
    Optional<Vehicle> findByPlateNumberIgnoreCaseForUpdate(@Param("plate") String plate);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    List<Vehicle> findByRegisteredOwnerIdOrderByIdAsc(Long ownerId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    List<Vehicle> findDistinctByAuthorizedMembers_IdOrderByIdAsc(Long memberId);

    List<Vehicle> findByHouseholdIdAndActiveTrue(Long householdId);

    List<Vehicle> findByRegisteredOwnerId(Long ownerId);

    List<Vehicle> findByAuthorizedMembers_Id(Long memberId);

    long countByCreatedAtBefore(LocalDateTime time);
}

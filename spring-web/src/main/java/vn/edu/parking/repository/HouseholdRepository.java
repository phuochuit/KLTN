package vn.edu.parking.repository;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import vn.edu.parking.domain.Household;
import java.util.Optional;
import java.time.LocalDateTime;

public interface HouseholdRepository extends JpaRepository<Household, Long> {
    Optional<Household> findByHouseholdCodeIgnoreCase(String householdCode);

    @Query(value = """
            select h from Household h
            where lower(h.householdCode) like :searchPattern escape '!'
               or lower(h.apartmentNumber) like :searchPattern escape '!'
               or lower(coalesce(h.buildingName, '')) like :searchPattern escape '!'
            """,
            countQuery = """
            select count(h) from Household h
            where lower(h.householdCode) like :searchPattern escape '!'
               or lower(h.apartmentNumber) like :searchPattern escape '!'
               or lower(coalesce(h.buildingName, '')) like :searchPattern escape '!'
            """)
    Page<Household> searchManagement(@Param("searchPattern") String searchPattern, Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select h from Household h where h.id = :id")
    Optional<Household> findByIdForUpdate(@Param("id") Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select h from Household h where lower(h.householdCode) = lower(:code)")
    Optional<Household> findByHouseholdCodeIgnoreCaseForUpdate(@Param("code") String code);

    @Query("select h.id from Household h where lower(h.householdCode) = lower(:code)")
    Optional<Long> findIdByHouseholdCodeIgnoreCase(@Param("code") String code);

    long countByCreatedAtBefore(LocalDateTime time);
}

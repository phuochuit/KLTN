package vn.edu.parking.repository;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import vn.edu.parking.domain.FamilyMember;
import java.util.List;
import java.util.Optional;

public interface FamilyMemberRepository extends JpaRepository<FamilyMember, Long> {
    List<FamilyMember> findAllByOrderByFullNameAsc();
    List<FamilyMember> findByHouseholdIdOrderByFullNameAsc(Long householdId);
    Optional<FamilyMember> findByCitizenId(String citizenId);

    @EntityGraph(attributePaths = "household")
    @Query(value = """
            select m from FamilyMember m join m.household h
            where lower(m.fullName) like :searchPattern escape '!'
               or lower(h.householdCode) like :searchPattern escape '!'
               or lower(h.apartmentNumber) like :searchPattern escape '!'
            """,
            countQuery = """
            select count(m) from FamilyMember m join m.household h
            where lower(m.fullName) like :searchPattern escape '!'
               or lower(h.householdCode) like :searchPattern escape '!'
               or lower(h.apartmentNumber) like :searchPattern escape '!'
            """)
    Page<FamilyMember> searchManagement(@Param("searchPattern") String searchPattern, Pageable pageable);

    @EntityGraph(attributePaths = "household")
    Page<FamilyMember> findByHouseholdId(Long householdId, Pageable pageable);

    @Query("select m from FamilyMember m join fetch m.household where m.id = :id")
    Optional<FamilyMember> findWithHouseholdById(@Param("id") Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select m from FamilyMember m where m.id = :id")
    Optional<FamilyMember> findByIdForUpdate(@Param("id") Long id);

    @Query("select m.id from FamilyMember m where m.citizenId = :citizenId")
    Optional<Long> findIdByCitizenId(@Param("citizenId") String citizenId);

    @Query("select m.household.id from FamilyMember m where m.id = :id")
    Optional<Long> findHouseholdIdById(@Param("id") Long id);
}

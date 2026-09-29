package vn.edu.parking.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import vn.edu.parking.domain.FamilyMember;
import java.util.List;
import java.util.Optional;

public interface FamilyMemberRepository extends JpaRepository<FamilyMember, Long> {
    List<FamilyMember> findAllByOrderByFullNameAsc();
    List<FamilyMember> findByHouseholdIdOrderByFullNameAsc(Long householdId);
    Optional<FamilyMember> findByCitizenId(String citizenId);
}

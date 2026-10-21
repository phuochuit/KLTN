package vn.edu.parking.repository;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import vn.edu.parking.domain.SecurityRole;

public interface SecurityRoleRepository extends JpaRepository<SecurityRole, Long> {
    Optional<SecurityRole> findByCode(String code);
    Optional<SecurityRole> findByEmployeePrefix(String employeePrefix);
    boolean existsByCode(String code);
    boolean existsByEmployeePrefix(String employeePrefix);
    List<SecurityRole> findAllByOrderByDisplayNameAsc();
    List<SecurityRole> findByEnabledTrueOrderByDisplayNameAsc();

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select role from SecurityRole role where role.id = :id")
    Optional<SecurityRole> lockById(@Param("id") Long id);
}

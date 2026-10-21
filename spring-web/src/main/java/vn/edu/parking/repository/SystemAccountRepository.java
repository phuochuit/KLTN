package vn.edu.parking.repository;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import vn.edu.parking.domain.AccountRole;
import vn.edu.parking.domain.SecurityPermission;
import vn.edu.parking.domain.SystemAccount;
import java.util.List;
import java.util.Optional;

public interface SystemAccountRepository extends JpaRepository<SystemAccount, Long> {
    Optional<SystemAccount> findByUsername(String username);
    Optional<SystemAccount> findByEmailIgnoreCase(String email);
    Optional<SystemAccount> findByCitizenId(String citizenId);
    List<SystemAccount> findAllByOrderByUsernameAsc();
    long countBySecurityRole_Id(Long roleId);
    List<SystemAccount> findBySecurityRole_Id(Long roleId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select account from SystemAccount account where account.username = :username")
    Optional<SystemAccount> lockByUsername(@Param("username") String username);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select account from SystemAccount account where account.role = :role and account.enabled = true order by account.id")
    List<SystemAccount> lockEnabledByRole(@Param("role") AccountRole role);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select distinct account from SystemAccount account join account.securityRole securityRole "
        + "join securityRole.permissions permission where permission = :permission "
        + "and account.enabled = true order by account.id")
    List<SystemAccount> lockEnabledByPermission(@Param("permission") SecurityPermission permission);
}

package vn.edu.parking.config;

import java.util.EnumSet;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import vn.edu.parking.domain.AccountRole;
import vn.edu.parking.domain.SecurityPermission;
import vn.edu.parking.domain.SecurityRole;
import vn.edu.parking.repository.SecurityRoleRepository;
import vn.edu.parking.repository.SystemAccountRepository;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class SecurityRoleBootstrap implements ApplicationRunner {
    private final SecurityRoleRepository roles;
    private final SystemAccountRepository accounts;

    public SecurityRoleBootstrap(SecurityRoleRepository roles, SystemAccountRepository accounts) {
        this.roles = roles;
        this.accounts = accounts;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        SecurityRole management = roles.findByCode(AccountRole.MANAGEMENT.name())
            .orElseGet(() -> roles.save(new SecurityRole(
                AccountRole.MANAGEMENT.name(), "Quản trị viên", "QT",
                "Vai trò hệ thống có toàn quyền quản trị.", true,
                EnumSet.allOf(SecurityPermission.class))));
        EnumSet<SecurityPermission> allPermissions = EnumSet.allOf(SecurityPermission.class);
        if (!management.getPermissions().equals(allPermissions)) {
            management.update(management.getDisplayName(), management.getDescription(), true, allPermissions);
            management = roles.save(management);
        }
        SecurityRole gateStaff = roles.findByCode(AccountRole.GATE_STAFF.name())
            .orElseGet(() -> roles.save(new SecurityRole(
                AccountRole.GATE_STAFF.name(), "Nhân viên gác cổng", "GC",
                "Vận hành trạm gác và xử lý kết quả nhận dạng trong ca trực.", true,
                EnumSet.of(SecurityPermission.DASHBOARD_VIEW,
                    SecurityPermission.GATE_OPERATE,
                    SecurityPermission.AI_RESULT_CORRECT))));

        for (var account : accounts.findAllByOrderByUsernameAsc()) {
            if (account.getSecurityRole() == null) {
                account.setSecurityRole(account.getRole() == AccountRole.MANAGEMENT
                    ? management : gateStaff);
            }
            if (account.getEmployeeCode() == null || account.getEmployeeCode().isBlank()) {
                SecurityRole lockedRole = roles.lockById(account.getSecurityRole().getId())
                    .orElseThrow(() -> new IllegalStateException("Security role disappeared during backfill"));
                account.setEmployeeCode(lockedRole.allocateEmployeeCode());
            }
        }
    }
}

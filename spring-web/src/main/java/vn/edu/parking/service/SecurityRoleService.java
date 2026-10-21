package vn.edu.parking.service;

import java.util.EnumSet;
import java.util.Locale;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.edu.parking.domain.AccountRole;
import vn.edu.parking.domain.SecurityPermission;
import vn.edu.parking.domain.SecurityRole;
import vn.edu.parking.repository.SecurityRoleRepository;
import vn.edu.parking.repository.SystemAccountRepository;

@Service
public class SecurityRoleService {
    private final SecurityRoleRepository roles;
    private final SystemAccountRepository accounts;
    private final SecurityAuditService audit;
    private final AccountSessionRevocationService sessionRevocation;

    public SecurityRoleService(SecurityRoleRepository roles, SystemAccountRepository accounts,
            SecurityAuditService audit, AccountSessionRevocationService sessionRevocation) {
        this.roles = roles;
        this.accounts = accounts;
        this.audit = audit;
        this.sessionRevocation = sessionRevocation;
    }

    @Transactional
    public SecurityRole create(String codeValue, String displayNameValue, String prefixValue,
            String descriptionValue, Set<SecurityPermission> permissions) {
        String code = normalizeCode(codeValue);
        String prefix = normalizePrefix(prefixValue);
        String displayName = requiredText(displayNameValue, 120, "Tên vai trò");
        String description = optionalText(descriptionValue, 500);
        if (roles.existsByCode(code)) throw new IllegalArgumentException("Mã vai trò đã tồn tại");
        if (roles.existsByEmployeePrefix(prefix))
            throw new IllegalArgumentException("Tiền tố mã nhân viên đã được sử dụng");
        var role = roles.save(new SecurityRole(code, displayName, prefix, description, false,
            copyPermissions(permissions)));
        audit.recordCurrent("SECURITY_ROLE_CREATE", "SECURITY_ROLE", role.getId().toString(),
            "SUCCESS", null, null);
        return role;
    }

    @Transactional
    public void update(Long id, String displayNameValue, String descriptionValue,
            boolean enabled, Set<SecurityPermission> permissions) {
        SecurityRole role = roles.lockById(id == null ? -1L : id)
            .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy vai trò"));
        String displayName = requiredText(displayNameValue, 120, "Tên vai trò");
        String description = optionalText(descriptionValue, 500);
        Set<SecurityPermission> validatedPermissions = copyPermissions(permissions);
        if (role.isSystemRole() && !enabled)
            throw new IllegalArgumentException("Không thể ngừng sử dụng vai trò hệ thống");
        if (!enabled && accounts.countBySecurityRole_Id(role.getId()) > 0)
            throw new IllegalArgumentException("Vai trò đang được gán cho tài khoản và không thể ngừng sử dụng");
        if (AccountRole.MANAGEMENT.name().equals(role.getCode())
                && (!validatedPermissions.contains(SecurityPermission.ACCOUNT_MANAGE)
                    || !validatedPermissions.contains(SecurityPermission.ROLE_MANAGE)))
            throw new IllegalArgumentException("Vai trò Quản trị phải giữ quyền quản lý tài khoản và vai trò");
        role.update(displayName, description, enabled, validatedPermissions);
        for (var account : accounts.findBySecurityRole_Id(role.getId())) {
            account.incrementSecurityVersion();
            sessionRevocation.revokeAllForAccount(account.getId());
        }
        audit.recordCurrent("SECURITY_ROLE_UPDATE", "SECURITY_ROLE", role.getId().toString(),
            "SUCCESS", null, null);
    }

    private static String normalizeCode(String value) {
        String code = value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
        if (!code.matches("[A-Z][A-Z0-9_]{2,47}"))
            throw new IllegalArgumentException("Mã vai trò phải có 3-48 ký tự A-Z, 0-9 hoặc dấu gạch dưới");
        return code;
    }

    private static String normalizePrefix(String value) {
        String prefix = value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
        if (!prefix.matches("[A-Z]{2,8}"))
            throw new IllegalArgumentException("Tiền tố nhân viên phải có 2-8 chữ cái A-Z");
        return prefix;
    }

    private static Set<SecurityPermission> copyPermissions(Set<SecurityPermission> permissions) {
        return permissions == null || permissions.isEmpty()
            ? EnumSet.noneOf(SecurityPermission.class) : EnumSet.copyOf(permissions);
    }

    private static String requiredText(String value, int max, String label) {
        String normalized = value == null ? "" : value.trim().replaceAll("\\s+", " ");
        if (normalized.isBlank() || normalized.length() > max)
            throw new IllegalArgumentException(label + " là bắt buộc và không được quá " + max + " ký tự");
        return normalized;
    }

    private static String optionalText(String value, int max) {
        String normalized = value == null ? "" : value.trim().replaceAll("\\s+", " ");
        if (normalized.length() > max) throw new IllegalArgumentException("Mô tả không được quá " + max + " ký tự");
        return normalized.isBlank() ? null : normalized;
    }
}

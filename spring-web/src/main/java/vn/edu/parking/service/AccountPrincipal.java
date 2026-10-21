package vn.edu.parking.service;

import java.util.Collection;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import vn.edu.parking.domain.AccountRole;
import vn.edu.parking.domain.SecurityPermission;
import vn.edu.parking.domain.SystemAccount;

public final class AccountPrincipal implements UserDetails {
    private final long accountId;
    private final String username;
    private final String passwordHash;
    private final AccountRole role;
    private final String roleCode;
    private final List<String> permissions;
    private final boolean enabled;
    private final boolean mustChangePassword;
    private final long securityVersion;

    AccountPrincipal(SystemAccount account) {
        accountId = account.getId() == null ? 0 : account.getId();
        username = account.getUsername();
        passwordHash = account.getPasswordHash();
        role = account.getRole();
        roleCode = account.getSecurityRole() == null
            ? role.name() : account.getSecurityRole().getCode();
        permissions = account.getSecurityRole() == null
            ? legacyPermissions(role)
            : account.getSecurityRole().getPermissions().stream().map(Enum::name).sorted().toList();
        enabled = account.isEnabled();
        mustChangePassword = account.isMustChangePassword();
        securityVersion = account.getSecurityVersion();
    }

    public long getAccountId() { return accountId; }
    public AccountRole getRole() { return role; }
    public boolean mustChangePassword() { return mustChangePassword; }
    public long getSecurityVersion() { return securityVersion; }
    @Override public Collection<? extends GrantedAuthority> getAuthorities() {
        if (mustChangePassword)
            return List.of(new SimpleGrantedAuthority("ROLE_PASSWORD_CHANGE_REQUIRED"));
        var authorities = new ArrayList<GrantedAuthority>(permissions.size() + 1);
        authorities.add(new SimpleGrantedAuthority("ROLE_" + roleCode));
        permissions.stream().map(SimpleGrantedAuthority::new).forEach(authorities::add);
        return List.copyOf(authorities);
    }
    @Override public String getPassword() { return passwordHash; }
    @Override public String getUsername() { return username; }
    @Override public boolean isAccountNonExpired() { return true; }
    @Override public boolean isAccountNonLocked() { return enabled; }
    @Override public boolean isCredentialsNonExpired() { return true; }
    @Override public boolean isEnabled() { return enabled; }

    private static List<String> legacyPermissions(AccountRole role) {
        var values = role == AccountRole.MANAGEMENT
            ? EnumSet.allOf(SecurityPermission.class)
            : EnumSet.of(SecurityPermission.DASHBOARD_VIEW, SecurityPermission.GATE_OPERATE,
                SecurityPermission.AI_RESULT_CORRECT);
        return values.stream().map(Enum::name).sorted().toList();
    }
}

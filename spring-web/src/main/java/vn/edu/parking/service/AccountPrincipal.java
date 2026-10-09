package vn.edu.parking.service;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import vn.edu.parking.domain.AccountRole;
import vn.edu.parking.domain.SystemAccount;

import java.util.Collection;
import java.util.List;

public final class AccountPrincipal implements UserDetails {
    private final long accountId;
    private final String username;
    private final String passwordHash;
    private final AccountRole role;
    private final boolean enabled;
    private final boolean mustChangePassword;
    private final long securityVersion;

    AccountPrincipal(SystemAccount account) {
        accountId = account.getId() == null ? 0 : account.getId();
        username = account.getUsername();
        passwordHash = account.getPasswordHash();
        role = account.getRole();
        enabled = account.isEnabled();
        mustChangePassword = account.isMustChangePassword();
        securityVersion = account.getSecurityVersion();
    }

    public long getAccountId() { return accountId; }
    public AccountRole getRole() { return role; }
    public boolean mustChangePassword() { return mustChangePassword; }
    public long getSecurityVersion() { return securityVersion; }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        String authority = mustChangePassword ? "ROLE_PASSWORD_CHANGE_REQUIRED" : "ROLE_" + role.name();
        return List.of(new SimpleGrantedAuthority(authority));
    }

    @Override public String getPassword() { return passwordHash; }
    @Override public String getUsername() { return username; }
    @Override public boolean isAccountNonExpired() { return true; }
    @Override public boolean isAccountNonLocked() { return enabled; }
    @Override public boolean isCredentialsNonExpired() { return true; }
    @Override public boolean isEnabled() { return enabled; }
}

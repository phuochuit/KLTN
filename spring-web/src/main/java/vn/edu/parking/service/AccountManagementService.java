package vn.edu.parking.service;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.edu.parking.domain.AccountRole;
import vn.edu.parking.domain.SystemAccount;
import vn.edu.parking.repository.SystemAccountRepository;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.Locale;

@Service
public class AccountManagementService {
    private static final SecureRandom RANDOM = new SecureRandom();

    private final SystemAccountRepository accounts;
    private final PasswordEncoder passwordEncoder;
    private final DesktopRefreshTokenService desktopSessions;
    private final SecurityAuditService audit;

    public AccountManagementService(SystemAccountRepository accounts, PasswordEncoder passwordEncoder,
            DesktopRefreshTokenService desktopSessions, SecurityAuditService audit) {
        this.accounts = accounts;
        this.passwordEncoder = passwordEncoder;
        this.desktopSessions = desktopSessions;
        this.audit = audit;
    }

    @Transactional
    public AccountProvisioning createAccount(String username, AccountRole role) {
        String normalized = normalizeUsername(username);
        if (role == null) throw new IllegalArgumentException("Role is required");
        if (accounts.findByUsername(normalized).isPresent()) throw new DuplicateAccountException();

        String temporaryPassword = newTemporaryPassword();
        var account = accounts.saveAndFlush(new vn.edu.parking.domain.SystemAccount(
            normalized, passwordEncoder.encode(temporaryPassword), role, true));
        audit.recordCurrent("ACCOUNT_CREATE", "SYSTEM_ACCOUNT", accountReference(account),
            "SUCCESS", null, null);
        return new AccountProvisioning(account, temporaryPassword);
    }

    @Transactional
    public void disable(String username) {
        var managementAccounts = accounts.lockEnabledByRole(AccountRole.MANAGEMENT);
        var account = findAccount(username);
        if (account.isEnabled() && account.getRole() == AccountRole.MANAGEMENT && managementAccounts.size() <= 1)
            throw new LastManagementAccountException();

        account.setEnabled(false);
        account.incrementSecurityVersion();
        accounts.save(account);
        desktopSessions.revokeAllForAccount(account.getId());
        audit.recordCurrent("ACCOUNT_DISABLE", "SYSTEM_ACCOUNT", accountReference(account),
            "SUCCESS", null, null);
    }

    @Transactional
    public void enable(String username) {
        var account = findAccount(username);
        account.setEnabled(true);
        account.incrementSecurityVersion();
        accounts.save(account);
        desktopSessions.revokeAllForAccount(account.getId());
        audit.recordCurrent("ACCOUNT_ENABLE", "SYSTEM_ACCOUNT", accountReference(account),
            "SUCCESS", null, null);
    }

    @Transactional
    public void changeRole(String username, AccountRole role) {
        if (role == null) throw new IllegalArgumentException("Role is required");
        var managementAccounts = accounts.lockEnabledByRole(AccountRole.MANAGEMENT);
        var account = findAccount(username);
        if (account.isEnabled() && account.getRole() == AccountRole.MANAGEMENT
                && role != AccountRole.MANAGEMENT && managementAccounts.size() <= 1) {
            throw new LastManagementAccountException();
        }

        account.setRole(role);
        account.incrementSecurityVersion();
        accounts.save(account);
        desktopSessions.revokeAllForAccount(account.getId());
        audit.recordCurrent("ACCOUNT_ROLE_CHANGE", "SYSTEM_ACCOUNT", accountReference(account),
            "SUCCESS", null, null);
    }

    @Transactional
    public String resetPassword(String username) {
        var account = findAccount(username);
        String temporaryPassword = newTemporaryPassword();

        account.setPasswordHash(passwordEncoder.encode(temporaryPassword));
        account.setMustChangePassword(true);
        account.incrementSecurityVersion();
        accounts.save(account);
        desktopSessions.revokeAllForAccount(account.getId());
        audit.recordCurrent("ACCOUNT_PASSWORD_RESET", "SYSTEM_ACCOUNT", accountReference(account),
            "SUCCESS", null, null);
        return temporaryPassword;
    }

    private static String newTemporaryPassword() {
        byte[] random = new byte[32];
        RANDOM.nextBytes(random);
        try {
            return Base64.getUrlEncoder().withoutPadding().encodeToString(random);
        } finally {
            java.util.Arrays.fill(random, (byte) 0);
        }
    }

    private static String normalizeUsername(String username) {
        String normalized = username == null ? "" : username.trim().toLowerCase(Locale.ROOT);
        if (normalized.isBlank() || normalized.length() > 80)
            throw new IllegalArgumentException("Username is required and must not exceed 80 characters");
        return normalized;
    }

    private static String accountReference(SystemAccount account) {
        return account.getId() == null ? account.getUsername() : account.getId().toString();
    }

    public record AccountProvisioning(vn.edu.parking.domain.SystemAccount account, String temporaryPassword) { }

    private vn.edu.parking.domain.SystemAccount findAccount(String username) {
        String normalized = username == null ? "" : username.trim().toLowerCase(Locale.ROOT);
        return accounts.lockByUsername(normalized)
            .orElseThrow(() -> new IllegalArgumentException("Account not found"));
    }
}

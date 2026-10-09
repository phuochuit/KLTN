package vn.edu.parking.service;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.edu.parking.repository.SystemAccountRepository;

import java.nio.charset.StandardCharsets;
import java.util.Locale;

@Service
public class AccountPasswordService {
    private static final int MIN_PASSWORD_LENGTH = 12;
    private static final int MAX_BCRYPT_BYTES = 72;

    private final SystemAccountRepository accounts;
    private final PasswordEncoder passwordEncoder;
    private final DesktopRefreshTokenService desktopSessions;
    private final SecurityAuditService audit;

    public AccountPasswordService(SystemAccountRepository accounts, PasswordEncoder passwordEncoder,
            DesktopRefreshTokenService desktopSessions, SecurityAuditService audit) {
        this.accounts = accounts;
        this.passwordEncoder = passwordEncoder;
        this.desktopSessions = desktopSessions;
        this.audit = audit;
    }

    @Transactional
    public void changeOwnPassword(String username, String currentPassword, String newPassword) {
        String normalized = username == null ? "" : username.trim().toLowerCase(Locale.ROOT);
        var account = accounts.lockByUsername(normalized)
            .orElseThrow(InvalidCurrentPasswordException::new);
        if (currentPassword == null || !passwordEncoder.matches(currentPassword, account.getPasswordHash()))
            throw new InvalidCurrentPasswordException();
        if (newPassword == null || newPassword.length() < MIN_PASSWORD_LENGTH
                || newPassword.getBytes(StandardCharsets.UTF_8).length > MAX_BCRYPT_BYTES) {
            throw new IllegalArgumentException("New password does not meet the length requirements");
        }

        account.setPasswordHash(passwordEncoder.encode(newPassword));
        account.setMustChangePassword(false);
        account.incrementSecurityVersion();
        accounts.save(account);
        desktopSessions.revokeAllForAccount(account.getId());
        String accountReference = account.getId() == null ? account.getUsername() : account.getId().toString();
        audit.recordCurrent("ACCOUNT_PASSWORD_CHANGE", "SYSTEM_ACCOUNT", accountReference,
            "SUCCESS", null, null);
    }
}

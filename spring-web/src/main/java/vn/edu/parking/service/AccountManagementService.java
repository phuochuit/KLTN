package vn.edu.parking.service;

import java.security.SecureRandom;
import java.time.LocalDate;
import java.time.Period;
import java.util.Base64;
import java.util.Locale;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.edu.parking.domain.AccountRole;
import vn.edu.parking.domain.SecurityPermission;
import vn.edu.parking.domain.SecurityRole;
import vn.edu.parking.domain.SystemAccount;
import vn.edu.parking.repository.SecurityRoleRepository;
import vn.edu.parking.repository.SystemAccountRepository;

@Service
public class AccountManagementService {
    private static final SecureRandom RANDOM = new SecureRandom();
    private final SystemAccountRepository accounts;
    private final SecurityRoleRepository roles;
    private final PasswordEncoder passwordEncoder;
    private final AccountSessionRevocationService sessionRevocation;
    private final SecurityAuditService audit;

    public AccountManagementService(SystemAccountRepository accounts, SecurityRoleRepository roles,
            PasswordEncoder passwordEncoder,
            AccountSessionRevocationService sessionRevocation, SecurityAuditService audit) {
        this.accounts = accounts;
        this.roles = roles;
        this.passwordEncoder = passwordEncoder;
        this.sessionRevocation = sessionRevocation;
        this.audit = audit;
    }

    @Transactional
    public AccountProvisioning createAccount(String username, AccountRole role) {
        return createAccount(username, username, role);
    }

    @Transactional
    public AccountProvisioning createAccount(String username, String fullName, AccountRole role) {
        String normalized = normalizeUsername(username);
        String normalizedFullName = normalizeFullName(fullName);
        if (role == null) throw new IllegalArgumentException("Role is required");
        if (accounts.findByUsername(normalized).isPresent()) {
            audit.recordCurrent("ACCOUNT_CREATE", "SYSTEM_ACCOUNT", normalized,
                "FAILURE", "ACCOUNT_EXISTS", null);
            throw new DuplicateAccountException();
        }
        String temporaryPassword = newTemporaryPassword();
        var account = accounts.saveAndFlush(new SystemAccount(normalized, normalizedFullName,
            passwordEncoder.encode(temporaryPassword), role, true));
        audit.recordCurrent("ACCOUNT_CREATE", "SYSTEM_ACCOUNT", reference(account), "SUCCESS", null, null);
        return new AccountProvisioning(account, temporaryPassword);
    }

    @Transactional
    public AccountProvisioning createAccount(String username, Long roleId, EmployeeProfile profile) {
        String normalized = normalizeUsername(username);
        EmployeeProfile valid = validateProfile(profile);
        if (roleId == null) throw new IllegalArgumentException("Vui lòng chọn vai trò");
        if (accounts.findByUsername(normalized).isPresent()) {
            audit.recordCurrent("ACCOUNT_CREATE", "SYSTEM_ACCOUNT", normalized,
                "FAILURE", "ACCOUNT_EXISTS", null);
            throw new DuplicateAccountException();
        }
        ensureProfileUnique(valid, null);
        SecurityRole securityRole = roles.lockById(roleId)
            .filter(SecurityRole::isEnabled)
            .orElseThrow(() -> new IllegalArgumentException("Vai trò không tồn tại hoặc đã ngừng sử dụng"));
        String temporaryPassword = newTemporaryPassword();
        var account = new SystemAccount(normalized, valid.fullName(),
            passwordEncoder.encode(temporaryPassword), compatibilityRole(securityRole), true);
        account.setSecurityRole(securityRole);
        account.setEmployeeCode(securityRole.allocateEmployeeCode());
        applyProfile(account, valid);
        account = accounts.saveAndFlush(account);
        audit.recordCurrent("ACCOUNT_CREATE", "SYSTEM_ACCOUNT", reference(account), "SUCCESS", null, null);
        return new AccountProvisioning(account, temporaryPassword);
    }

    @Transactional
    public void updateProfile(String username, Long roleId, EmployeeProfile profile) {
        EmployeeProfile valid = validateProfile(profile);
        var account = findAccount(username);
        ensureProfileUnique(valid, account.getId());
        SecurityRole securityRole = roles.findById(roleId == null ? -1L : roleId)
            .filter(SecurityRole::isEnabled)
            .orElseThrow(() -> new IllegalArgumentException("Vai trò không tồn tại hoặc đã ngừng sử dụng"));
        boolean roleChanged = account.getSecurityRole() == null
            || !java.util.Objects.equals(account.getSecurityRole().getId(), securityRole.getId());
        if (roleChanged) {
            protectLastManagementAccount(account, securityRole);
            account.setSecurityRole(securityRole);
            account.setRole(compatibilityRole(securityRole));
        }
        applyProfile(account, valid);
        if (roleChanged) updateSecurityState(account, "ACCOUNT_PROFILE_AND_ROLE_UPDATE");
        else {
            accounts.save(account);
            audit.recordCurrent("ACCOUNT_PROFILE_UPDATE", "SYSTEM_ACCOUNT", reference(account),
                "SUCCESS", null, null);
        }
    }

    @Transactional
    public void disable(String username) {
        var managementAccounts = lockEnabledRoleManagers();
        var account = findAccount(username);
        if (account.isEnabled() && canManageRoles(account) && managementAccounts.size() <= 1) {
            audit.recordCurrent("ACCOUNT_DISABLE", "SYSTEM_ACCOUNT", reference(account),
                "FAILURE", "LAST_MANAGEMENT_ACCOUNT", null);
            throw new LastManagementAccountException();
        }
        account.setEnabled(false);
        updateSecurityState(account, "ACCOUNT_DISABLE");
    }

    @Transactional
    public void enable(String username) {
        var account = findAccount(username);
        account.setEnabled(true);
        updateSecurityState(account, "ACCOUNT_ENABLE");
    }

    @Transactional
    public void changeRole(String username, AccountRole role) {
        if (role == null) throw new IllegalArgumentException("Role is required");
        var managementAccounts = accounts.lockEnabledByRole(AccountRole.MANAGEMENT);
        var account = findAccount(username);
        if (account.isEnabled() && account.getRole() == AccountRole.MANAGEMENT
                && role != AccountRole.MANAGEMENT && managementAccounts.size() <= 1) {
            audit.recordCurrent("ACCOUNT_ROLE_CHANGE", "SYSTEM_ACCOUNT", reference(account),
                "FAILURE", "LAST_MANAGEMENT_ACCOUNT", null);
            throw new LastManagementAccountException();
        }
        account.setRole(role);
        updateSecurityState(account, "ACCOUNT_ROLE_CHANGE");
    }

    @Transactional
    public void changeFullName(String username, String fullName) {
        var account = findAccount(username);
        account.setFullName(normalizeFullName(fullName));
        accounts.save(account);
        audit.recordCurrent("ACCOUNT_FULL_NAME_CHANGE", "SYSTEM_ACCOUNT", reference(account),
            "SUCCESS", null, null);
    }

    @Transactional
    public String resetPassword(String username) {
        var account = findAccount(username);
        String temporaryPassword = newTemporaryPassword();
        account.setPasswordHash(passwordEncoder.encode(temporaryPassword));
        account.setMustChangePassword(true);
        updateSecurityState(account, "ACCOUNT_PASSWORD_RESET");
        return temporaryPassword;
    }

    private void updateSecurityState(SystemAccount account, String action) {
        account.incrementSecurityVersion();
        accounts.save(account);
        sessionRevocation.revokeAllForAccount(account.getId());
        audit.recordCurrent(action, "SYSTEM_ACCOUNT", reference(account), "SUCCESS", null, null);
    }

    private SystemAccount findAccount(String username) {
        String normalized = normalizeUsername(username);
        return accounts.lockByUsername(normalized).orElseThrow(() -> {
            audit.recordCurrent("ACCOUNT_LOOKUP", "SYSTEM_ACCOUNT", normalized,
                "FAILURE", "ACCOUNT_NOT_FOUND", null);
            return new AccountNotFoundException();
        });
    }

    private static String normalizeUsername(String username) {
        String normalized = username == null ? "" : username.trim().toLowerCase(Locale.ROOT);
        if (normalized.isBlank() || normalized.length() > 80)
            throw new IllegalArgumentException("Username is required and must not exceed 80 characters");
        return normalized;
    }

    private static String normalizeFullName(String fullName) {
        String normalized = fullName == null ? "" : fullName.trim().replaceAll("\\s+", " ");
        if (normalized.isBlank() || normalized.length() > 160)
            throw new IllegalArgumentException("Họ và tên là bắt buộc và không được quá 160 ký tự");
        return normalized;
    }

    private void ensureProfileUnique(EmployeeProfile profile, Long currentAccountId) {
        accounts.findByEmailIgnoreCase(profile.email())
            .filter(existing -> !existing.getId().equals(currentAccountId))
            .ifPresent(existing -> { throw new IllegalArgumentException("Email đã được sử dụng"); });
        accounts.findByCitizenId(profile.citizenId())
            .filter(existing -> !existing.getId().equals(currentAccountId))
            .ifPresent(existing -> { throw new IllegalArgumentException("CCCD đã được sử dụng"); });
    }

    private void protectLastManagementAccount(SystemAccount account, SecurityRole replacement) {
        if (!account.isEnabled() || account.getSecurityRole() == null
                || !account.getSecurityRole().getPermissions().contains(SecurityPermission.ROLE_MANAGE)
                || replacement.getPermissions().contains(SecurityPermission.ROLE_MANAGE)) return;
        if (lockEnabledRoleManagers().size() <= 1) throw new LastManagementAccountException();
    }

    private java.util.List<SystemAccount> lockEnabledRoleManagers() {
        var byId = new java.util.LinkedHashMap<Long, SystemAccount>();
        accounts.lockEnabledByPermission(SecurityPermission.ROLE_MANAGE)
            .forEach(account -> byId.put(account.getId(), account));
        accounts.lockEnabledByRole(AccountRole.MANAGEMENT).stream()
            .filter(account -> account.getSecurityRole() == null)
            .forEach(account -> byId.put(account.getId(), account));
        return java.util.List.copyOf(byId.values());
    }

    private static boolean canManageRoles(SystemAccount account) {
        return account.getSecurityRole() == null
            ? account.getRole() == AccountRole.MANAGEMENT
            : account.getSecurityRole().getPermissions().contains(SecurityPermission.ROLE_MANAGE);
    }

    private static AccountRole compatibilityRole(SecurityRole role) {
        return AccountRole.MANAGEMENT.name().equals(role.getCode())
            ? AccountRole.MANAGEMENT : AccountRole.GATE_STAFF;
    }

    private static EmployeeProfile validateProfile(EmployeeProfile profile) {
        if (profile == null) throw new IllegalArgumentException("Thông tin nhân viên là bắt buộc");
        String fullName = normalizeFullName(profile.fullName());
        String phone = compact(profile.phone());
        if (!phone.matches("\\+?[0-9]{9,15}"))
            throw new IllegalArgumentException("Số điện thoại phải có từ 9 đến 15 chữ số");
        String email = profile.email() == null ? "" : profile.email().trim().toLowerCase(Locale.ROOT);
        if (email.length() > 160 || !email.matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$"))
            throw new IllegalArgumentException("Email không hợp lệ");
        String citizenId = compact(profile.citizenId());
        if (!citizenId.matches("[0-9]{9}|[0-9]{12}"))
            throw new IllegalArgumentException("CCCD/CMND phải gồm 9 hoặc 12 chữ số");
        LocalDate birthDate = profile.dateOfBirth();
        if (birthDate == null || birthDate.isAfter(LocalDate.now().minusYears(18))
                || birthDate.isBefore(LocalDate.now().minusYears(100)))
            throw new IllegalArgumentException("Ngày sinh không hợp lệ; nhân viên phải từ 18 tuổi");
        String address = normalizeText(profile.address(), 500, "Địa chỉ");
        return new EmployeeProfile(fullName, phone, email, citizenId, birthDate, address);
    }

    private static void applyProfile(SystemAccount account, EmployeeProfile profile) {
        account.setFullName(profile.fullName());
        account.setPhone(profile.phone());
        account.setEmail(profile.email());
        account.setCitizenId(profile.citizenId());
        account.setDateOfBirth(profile.dateOfBirth());
        account.setAddress(profile.address());
    }

    private static String compact(String value) {
        return value == null ? "" : value.trim().replaceAll("[\\s.-]", "");
    }

    private static String normalizeText(String value, int maxLength, String label) {
        String normalized = value == null ? "" : value.trim().replaceAll("\\s+", " ");
        if (normalized.isBlank() || normalized.length() > maxLength)
            throw new IllegalArgumentException(label + " là bắt buộc và không được quá " + maxLength + " ký tự");
        return normalized;
    }

    private static String newTemporaryPassword() {
        byte[] random = new byte[32];
        RANDOM.nextBytes(random);
        try { return Base64.getUrlEncoder().withoutPadding().encodeToString(random); }
        finally { java.util.Arrays.fill(random, (byte) 0); }
    }

    private static String reference(SystemAccount account) {
        return account.getId() == null ? account.getUsername() : account.getId().toString();
    }

    public record AccountProvisioning(SystemAccount account, String temporaryPassword) { }
    public record EmployeeProfile(String fullName, String phone, String email, String citizenId,
            LocalDate dateOfBirth, String address) { }
}

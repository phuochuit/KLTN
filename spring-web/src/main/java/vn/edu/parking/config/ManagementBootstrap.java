package vn.edu.parking.config;

import java.nio.charset.StandardCharsets;
import java.util.Locale;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import vn.edu.parking.domain.AccountRole;
import vn.edu.parking.domain.SystemAccount;
import vn.edu.parking.repository.SecurityRoleRepository;
import vn.edu.parking.repository.SystemAccountRepository;

@Component
public class ManagementBootstrap implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(ManagementBootstrap.class);

    private final SystemAccountRepository accounts;
    private final PasswordEncoder passwordEncoder;
    private final SecurityRoleRepository roles;
    private final String username;
    private final String initialPassword;
    private final String demoPassword;

    public ManagementBootstrap(SystemAccountRepository accounts, PasswordEncoder passwordEncoder,
            SecurityRoleRepository roles,
            @Value("${parking.security.bootstrap.username:}") String username,
            @Value("${parking.security.bootstrap.password:}") String initialPassword,
            @Value("${parking.security.bootstrap.demo-password:}") String demoPassword) {
        this.accounts = accounts;
        this.passwordEncoder = passwordEncoder;
        this.roles = roles;
        this.username = username;
        this.initialPassword = initialPassword;
        this.demoPassword = demoPassword;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (username.isBlank() && initialPassword.isBlank()) {
            seedDemoAccountWhenEmpty();
            return;
        }
        if (username.isBlank() || initialPassword.isBlank())
            throw new IllegalStateException("Both bootstrap username and password must be configured");
        validatePassword(initialPassword);
        String normalized = username.trim().toLowerCase(Locale.ROOT);
        if (normalized.length() > 80) throw new IllegalStateException("Bootstrap username is too long");
        if (accounts.findByUsername(normalized).isPresent()) {
            log.info("Management bootstrap account '{}' already exists; leaving it unchanged", normalized);
            return;
        }
        createAccount(normalized, initialPassword, "bootstrap");
    }

    /**
     * Cơ sở dữ liệu mới (H2 của bản demo hoặc MySQL trống) không có tài khoản nào nên không thể
     * đăng nhập. Khi chưa cấu hình bootstrap, tạo sẵn một tài khoản quản trị mẫu và buộc đổi mật
     * khẩu ngay sau lần đăng nhập đầu tiên. Đặt {@code parking.security.bootstrap.demo-password}
     * thành rỗng để tắt hoàn toàn (ví dụ khi triển khai thật).
     */
    private void seedDemoAccountWhenEmpty() {
        if (demoPassword.isBlank()) {
            log.info("Management account bootstrap is not configured");
            return;
        }
        long existing = accounts.count();
        if (existing > 0) {
            log.info("Management bootstrap skipped: {} account(s) already exist", existing);
            return;
        }
        createAccount("admin", demoPassword, "demo");
    }

    private void createAccount(String normalizedUsername, String rawPassword, String origin) {
        validatePassword(rawPassword);
        var role = roles.findByCode(AccountRole.MANAGEMENT.name())
            .orElseThrow(() -> new IllegalStateException("Management security role is missing"));
        var account = new SystemAccount(normalizedUsername, passwordEncoder.encode(rawPassword),
            AccountRole.MANAGEMENT, true);
        account.setSecurityRole(role);
        account.setEmployeeCode(role.allocateEmployeeCode());
        accounts.save(account);
        log.info("Created {} management account '{}' with mandatory password change", origin,
            normalizedUsername);
    }

    private static void validatePassword(String rawPassword) {
        if (rawPassword.length() < 12 || rawPassword.getBytes(StandardCharsets.UTF_8).length > 72)
            throw new IllegalStateException("Bootstrap password must be between 12 and 72 UTF-8 bytes");
    }
}

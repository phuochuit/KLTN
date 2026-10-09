package vn.edu.parking.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import vn.edu.parking.domain.AccountRole;
import vn.edu.parking.domain.SystemAccount;
import vn.edu.parking.repository.SystemAccountRepository;

@Component
public class ManagementBootstrap implements ApplicationRunner {
    private final SystemAccountRepository accounts;
    private final PasswordEncoder passwordEncoder;
    private final String username;
    private final String initialPassword;

    public ManagementBootstrap(SystemAccountRepository accounts, PasswordEncoder passwordEncoder,
            @Value("${parking.security.bootstrap.username:}") String username,
            @Value("${parking.security.bootstrap.password:}") String initialPassword) {
        this.accounts = accounts;
        this.passwordEncoder = passwordEncoder;
        this.username = username;
        this.initialPassword = initialPassword;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (username.isBlank() && initialPassword.isBlank()) return;
        if (username.isBlank() || initialPassword.isBlank())
            throw new IllegalStateException("Both bootstrap username and password must be configured");
        String normalized = username.trim().toLowerCase(java.util.Locale.ROOT);
        if (accounts.findByUsername(normalized).isPresent()) return;
        accounts.save(new SystemAccount(normalized, passwordEncoder.encode(initialPassword),
            AccountRole.MANAGEMENT, true));
    }
}

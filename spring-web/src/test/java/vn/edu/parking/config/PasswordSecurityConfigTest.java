package vn.edu.parking.config;

import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PasswordSecurityConfigTest {
    private final PasswordEncoder encoder = new PasswordSecurityConfig().passwordEncoder();

    @Test
    void storesBcryptHashWithRequiredCostAndNeverPlaintext() {
        String password = "test-only synthetic bootstrap password";

        String encoded = encoder.encode(password);

        assertTrue(encoded.startsWith("{bcrypt}$2a$12$") || encoded.startsWith("{bcrypt}$2b$12$")
            || encoded.startsWith("{bcrypt}$2y$12$"));
        assertFalse(encoded.equals(password));
        assertTrue(encoder.matches(password, encoded));
    }
}

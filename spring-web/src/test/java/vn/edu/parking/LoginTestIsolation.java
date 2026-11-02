package vn.edu.parking;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;
import org.springframework.test.context.DynamicPropertyRegistry;

final class LoginTestIsolation {
    static final String PASSWORD = "LumiPark-test-only-2026!";

    private LoginTestIsolation() { }

    static void register(DynamicPropertyRegistry registry, String label) throws IOException {
        Path cache = workspaceRoot().resolve(".cache/test-temp");
        Files.createDirectories(cache);
        Path media = Files.createTempDirectory(cache, label + "-");
        Map<String, String> properties = Map.ofEntries(
            Map.entry("spring.datasource.url", "jdbc:h2:mem:" + label + "-" + UUID.randomUUID()
                + ";DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE"),
            Map.entry("spring.datasource.driver-class-name", "org.h2.Driver"),
            Map.entry("spring.datasource.type", "com.zaxxer.hikari.HikariDataSource"),
            Map.entry("spring.datasource.username", "sa"),
            Map.entry("spring.datasource.password", ""),
            Map.entry("spring.jpa.database-platform", "org.hibernate.dialect.H2Dialect"),
            Map.entry("spring.jpa.hibernate.ddl-auto", "create-drop"),
            Map.entry("spring.jpa.open-in-view", "false"),
            Map.entry("spring.jpa.show-sql", "false"),
            Map.entry("spring.sql.init.mode", "never"),
            Map.entry("spring.flyway.enabled", "false"),
            Map.entry("spring.liquibase.enabled", "false"),
            Map.entry("spring.h2.console.enabled", "false"),
            Map.entry("parking.upload-dir", media.resolve("uploads").toString()),
            Map.entry("parking.private-storage-path", media.resolve("private").toString()),
            Map.entry("spring.servlet.multipart.location", media.toString()),
            Map.entry("parking.security.bootstrap.username", ""),
            Map.entry("parking.security.bootstrap.password", ""),
            Map.entry("parking.security.bootstrap.demo-password", ""),
            Map.entry("parking.security.jwt.enabled", "false"),
            Map.entry("parking.ai.base-url", "http://127.0.0.1:1"),
            Map.entry("parking.ai.service-token", ""),
            Map.entry("parking.ai.connect-timeout", "100ms"),
            Map.entry("parking.ai.read-timeout", "100ms"),
            Map.entry("parking.evidence.cleanup-enabled", "false"),
            Map.entry("server.address", "127.0.0.1"),
            Map.entry("server.port", "0"),
            // Only these isolated loopback tests use HTTP rather than production TLS.
            Map.entry("server.servlet.session.cookie.secure", "false"),
            Map.entry("server.servlet.session.cookie.http-only", "true"),
            Map.entry("server.servlet.session.cookie.same-site", "lax"),
            Map.entry("logging.level.org.springframework.security", "OFF"),
            Map.entry("logging.level.org.springframework.web", "OFF"),
            Map.entry("logging.level.org.hibernate.SQL", "OFF"),
            Map.entry("logging.level.org.hibernate.orm.jdbc.bind", "OFF")
        );
        properties.forEach((name, value) -> registry.add(name, () -> value));
    }

    static Path workspaceRoot() throws IOException {
        for (Path path = Path.of("").toAbsolutePath().normalize(); path != null; path = path.getParent()) {
            if (Files.isRegularFile(path.resolve("spring-web/pom.xml"))) return path.toRealPath();
        }
        throw new IllegalStateException("Run login tests from the workspace root or spring-web");
    }
}

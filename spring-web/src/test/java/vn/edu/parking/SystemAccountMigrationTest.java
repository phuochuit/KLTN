package vn.edu.parking;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;

import java.sql.DriverManager;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SystemAccountMigrationTest {
    @Test
    void createsAdditiveAccountSchemaOnIsolatedH2MysqlModeDatabase() throws Exception {
        String url = "jdbc:h2:mem:security-migration-test;MODE=MySQL;DB_CLOSE_DELAY=-1";
        try (var connection = DriverManager.getConnection(url, "sa", "");
            var statement = connection.createStatement()) {
            // The real application schema predates these opt-in security migrations.
            statement.execute("create table parking_sessions (id bigint auto_increment primary key)");
            statement.execute("create table family_members (id bigint auto_increment primary key)");
        }
        Flyway flyway = Flyway.configure()
            .dataSource(url, "sa", "")
            .locations("classpath:db/migration")
            .baselineVersion("0")
            .load();
        flyway.baseline();
        flyway.migrate();

        try (var connection = DriverManager.getConnection(url, "sa", "")) {
            for (String table : new String[]{"SYSTEM_ACCOUNTS", "DESKTOP_SESSIONS", "DESKTOP_REFRESH_TOKENS",
                    "SECURITY_AUDIT_EVENTS", "GATE_EVIDENCE"}) {
                try (var statement = connection.createStatement();
                     var result = statement.executeQuery("select count(*) from information_schema.tables " +
                         "where table_schema = 'PUBLIC' and table_name = '" + table + "'")) {
                    result.next();
                    assertEquals(1, result.getInt(1), "Missing migration table " + table);
                }
            }
        }

        try (var connection = DriverManager.getConnection(url, "sa", "");
             var insert = connection.prepareStatement("insert into system_accounts " +
                 "(username, password_hash, role, enabled, must_change_password, security_version, created_at) " +
                 "values (?, ?, ?, ?, ?, ?, ?)", java.sql.Statement.RETURN_GENERATED_KEYS)) {
            insert.setString(1, "migration-test-account");
            insert.setString(2, "{bcrypt}synthetic-test-hash");
            insert.setString(3, "MANAGEMENT");
            insert.setBoolean(4, true);
            insert.setBoolean(5, true);
            insert.setLong(6, 0);
            insert.setTimestamp(7, Timestamp.from(Instant.now()));
            assertEquals(1, insert.executeUpdate());
            long accountId;
            try (var keys = insert.getGeneratedKeys()) {
                keys.next();
                accountId = keys.getLong(1);
            }
            String sessionId = UUID.randomUUID().toString();
            Instant now = Instant.now();
            try (var sessionInsert = connection.prepareStatement("insert into desktop_sessions " +
                    "(session_id, account_id, created_at, last_seen_at, absolute_expires_at, security_version, device_label) " +
                    "values (?, ?, ?, ?, ?, ?, ?)")) {
                sessionInsert.setString(1, sessionId);
                sessionInsert.setLong(2, accountId);
                sessionInsert.setTimestamp(3, Timestamp.from(now));
                sessionInsert.setTimestamp(4, Timestamp.from(now));
                sessionInsert.setTimestamp(5, Timestamp.from(now.plusSeconds(36000)));
                sessionInsert.setLong(6, 0);
                sessionInsert.setString(7, "migration-test-device");
                assertEquals(1, sessionInsert.executeUpdate());
            }
            try (var tokenInsert = connection.prepareStatement("insert into desktop_refresh_tokens " +
                    "(session_id, token_hash, created_at, expires_at) values (?, ?, ?, ?)")) {
                tokenInsert.setString(1, sessionId);
                tokenInsert.setString(2, "a".repeat(64));
                tokenInsert.setTimestamp(3, Timestamp.from(now));
                tokenInsert.setTimestamp(4, Timestamp.from(now.plusSeconds(36000)));
                assertEquals(1, tokenInsert.executeUpdate());
            }
            try (var evidenceInsert = connection.prepareStatement("insert into gate_evidence " +
                    "(id, owner_account_id, desktop_session_id, evidence_kind, file_name, media_type, byte_size, " +
                    "sha256, operation_type, captured_at, expires_at, created_at) " +
                    "values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)")) {
                evidenceInsert.setString(1, UUID.randomUUID().toString());
                evidenceInsert.setLong(2, accountId);
                evidenceInsert.setString(3, sessionId);
                evidenceInsert.setString(4, "PLATE_RECOGNITION");
                evidenceInsert.setString(5, UUID.randomUUID() + ".jpg");
                evidenceInsert.setString(6, "image/jpeg");
                evidenceInsert.setLong(7, 3);
                evidenceInsert.setString(8, "a".repeat(64));
                evidenceInsert.setString(9, "ENTRY");
                evidenceInsert.setTimestamp(10, Timestamp.from(now));
                evidenceInsert.setTimestamp(11, Timestamp.from(now.plusSeconds(2592000)));
                evidenceInsert.setTimestamp(12, Timestamp.from(now));
                assertEquals(1, evidenceInsert.executeUpdate());
            }
            try (var auditInsert = connection.prepareStatement("insert into security_audit_events " +
                    "(actor_account_id, actor_username, action, target_type, target_reference, outcome, reason, " +
                    "evidence_reference, occurred_at) values (?, ?, ?, ?, ?, ?, ?, ?, ?)")) {
                auditInsert.setLong(1, accountId);
                auditInsert.setString(2, "migration-test-account");
                auditInsert.setString(3, "ACCOUNT_CREATE");
                auditInsert.setString(4, "SYSTEM_ACCOUNT");
                auditInsert.setString(5, Long.toString(accountId));
                auditInsert.setString(6, "SUCCESS");
                auditInsert.setString(7, null);
                auditInsert.setString(8, null);
                auditInsert.setTimestamp(9, Timestamp.from(now));
                assertEquals(1, auditInsert.executeUpdate());
            }
        }
    }
}

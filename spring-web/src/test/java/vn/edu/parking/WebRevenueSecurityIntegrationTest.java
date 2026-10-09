package vn.edu.parking;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import vn.edu.parking.domain.AccountRole;
import vn.edu.parking.domain.SystemAccount;
import vn.edu.parking.repository.SystemAccountRepository;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
    "spring.datasource.url=jdbc:h2:mem:parking-revenue-test;DB_CLOSE_DELAY=-1",
    "spring.datasource.username=sa",
    "spring.datasource.password=",
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.jpa.hibernate.ddl-auto=create-drop",
    "spring.flyway.enabled=false",
    "parking.security.jwt.enabled=true",
    "parking.upload-dir=target/test-revenue-uploads",
    "parking.gate-evidence.directory=target/test-revenue-gate-evidence"
})
class WebRevenueSecurityIntegrationTest {
    private static final java.security.KeyPair TEST_JWT_KEY_PAIR = createTestJwtKeyPair();

    @DynamicPropertySource
    static void configureTestJwtKeys(DynamicPropertyRegistry properties) {
        properties.add("parking.security.jwt.public-key-pem", () -> publicPem(TEST_JWT_KEY_PAIR.getPublic()));
        properties.add("parking.security.jwt.private-key-pem", () -> privatePem(TEST_JWT_KEY_PAIR.getPrivate()));
        properties.add("parking.security.jwt.issuer", () -> "https://parking.test");
        properties.add("parking.security.jwt.audience", () -> "parking-desktop");
        properties.add("parking.security.jwt.kid", () -> "revenue-test-kid");
    }

    @Autowired MockMvc mvc;
    @Autowired SystemAccountRepository accounts;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired vn.edu.parking.repository.SecurityAuditRepository auditEvents;

    @Test
    void anonymousRevenueRequestsReceiveWebJsonUnauthorized() throws Exception {
        long previousAuditId = latestCccdAuditId();
        for (String path : webJsonPaths()) {
            mvc.perform(get(path).accept("application/json"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith("application/json"))
                .andExpect(jsonPath("$.error").value("unauthorized"));
        }

        mvc.perform(multipart("/api/cccd/scan-qr").with(csrf()).accept("application/json"))
            .andExpect(status().isUnauthorized())
            .andExpect(content().contentTypeCompatibleWith("application/json"));

        var deniedAudit = auditEvents.findAll().stream()
            .filter(event -> event.getId() > previousAuditId && event.getAction().equals("CCCD_QR_ACCESS_DENIED"))
            .toList();
        assertEquals(1, deniedAudit.size());
        assertEquals("UNAUTHENTICATED", deniedAudit.get(0).getReason());

        mvc.perform(options("/api/cccd/scan-qr")
                .header("Origin", "https://untrusted.example")
                .header("Access-Control-Request-Method", "POST"))
            .andExpect(status().isUnauthorized())
            .andExpect(header().doesNotExist("Access-Control-Allow-Origin"));
    }

    @Test
    void managementWebSessionCanReadRevenueAndWebCsrfRemainsEnabled() throws Exception {
        MockHttpSession session = login(AccountRole.MANAGEMENT);

        for (String path : revenuePaths()) {
            mvc.perform(get(path).session(session).accept("application/json"))
                .andExpect(status().isOk());
        }
        mvc.perform(get("/account-admin/accounts").session(session).accept("application/json"))
            .andExpect(status().isOk());
        mvc.perform(post("/api/parking/revenue/monthly").session(session))
            .andExpect(status().isForbidden());
        mvc.perform(post("/api/parking/revenue/monthly").session(session).with(csrf()))
            .andExpect(status().isMethodNotAllowed());
    }

    @Test
    void gateStaffWebSessionCannotReadRevenue() throws Exception {
        MockHttpSession session = login(AccountRole.GATE_STAFF);
        long previousAuditId = latestCccdAuditId();

        for (String path : webJsonPaths()) {
            mvc.perform(get(path).session(session).accept("application/json"))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith("application/json"))
                .andExpect(jsonPath("$.error").value("forbidden"));
        }
        mvc.perform(multipart("/api/cccd/scan-qr").session(session).with(csrf()).accept("application/json"))
            .andExpect(status().isForbidden())
            .andExpect(content().contentTypeCompatibleWith("application/json"));
        var deniedAudit = auditEvents.findAll().stream()
            .filter(event -> event.getId() > previousAuditId && event.getAction().equals("CCCD_QR_ACCESS_DENIED"))
            .toList();
        assertEquals(1, deniedAudit.size());
        assertEquals("INSUFFICIENT_ROLE", deniedAudit.get(0).getReason());
    }

    private long latestCccdAuditId() {
        return auditEvents.findAll().stream()
            .filter(event -> event.getAction().equals("CCCD_QR_ACCESS_DENIED"))
            .mapToLong(vn.edu.parking.domain.SecurityAuditEvent::getId).max().orElse(0);
    }

    private MockHttpSession login(AccountRole role) throws Exception {
        String username = "revenue-" + role.name().toLowerCase() + "-" + java.util.UUID.randomUUID();
        String password = "synthetic revenue account password";
        accounts.saveAndFlush(new SystemAccount(username, passwordEncoder.encode(password), role, false));

        var result = mvc.perform(post("/login").with(csrf())
                .param("username", username).param("password", password))
            .andExpect(status().is3xxRedirection())
            .andReturn();
        MockHttpSession session = (MockHttpSession) result.getRequest().getSession(false);
        assertNotNull(session, "successful Web login should create an HTTP session");
        return session;
    }

    private static String[] revenuePaths() {
        int year = java.time.LocalDate.now().getYear();
        int month = java.time.LocalDate.now().getMonthValue();
        return new String[]{
            "/api/parking/revenue/monthly",
            "/api/parking/revenue/filter?type=month&year=" + year + "&month=" + month,
            "/api/parking/revenue/monthly/" + year + "/" + month,
            "/api/parking/revenue/stats/" + year + "/" + month
        };
    }

    private static String[] webJsonPaths() {
        return java.util.stream.Stream.concat(java.util.Arrays.stream(revenuePaths()),
            java.util.stream.Stream.of("/account-admin/accounts", "/admin-data/vehicles/1"))
            .toArray(String[]::new);
    }

    private static java.security.KeyPair createTestJwtKeyPair() {
        try {
            var generator = java.security.KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            return generator.generateKeyPair();
        } catch (java.security.GeneralSecurityException ex) {
            throw new IllegalStateException("Unable to create synthetic integration-test key", ex);
        }
    }

    private static String publicPem(java.security.PublicKey key) {
        return "-----BEGIN PUBLIC KEY-----\n" +
            java.util.Base64.getMimeEncoder(64, new byte[]{'\n'}).encodeToString(key.getEncoded()) +
            "\n-----END PUBLIC KEY-----";
    }

    private static String privatePem(java.security.PrivateKey key) {
        return "-----BEGIN PRIVATE KEY-----\n" +
            java.util.Base64.getMimeEncoder(64, new byte[]{'\n'}).encodeToString(key.getEncoded()) +
            "\n-----END PRIVATE KEY-----";
    }
}

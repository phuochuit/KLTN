package vn.edu.parking.config;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import com.fasterxml.jackson.databind.ObjectMapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DesktopJwtConfigurationTest {
    private KeyPair pair;
    private NimbusJwtEncoder encoder;
    private DesktopJwtConfiguration configuration;

    @BeforeEach
    void setUp() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        pair = generator.generateKeyPair();
        RSAKey signingKey = new RSAKey.Builder((java.security.interfaces.RSAPublicKey) pair.getPublic())
            .privateKey((java.security.interfaces.RSAPrivateKey) pair.getPrivate())
            .keyID("test-kid")
            .build();
        encoder = new NimbusJwtEncoder((selector, context) -> selector.select(new JWKSet(signingKey)));
        configuration = new DesktopJwtConfiguration();
    }

    @Test
    void acceptsOnlySignedTokensWithIssuerAudienceAndSessionClaims() {
        var decoder = configuration.jwtDecoder(publicPem(pair), "https://parking.test", "parking-desktop", "test-kid");

        var jwt = decoder.decode(token("https://parking.test", List.of("parking-desktop"), true));

        assertEquals("account-17", jwt.getSubject());
        assertEquals("session-23", jwt.getClaimAsString("sid"));
    }

    @Test
    void rejectsWrongIssuerAudienceAndMissingSessionBinding() {
        var decoder = configuration.jwtDecoder(publicPem(pair), "https://parking.test", "parking-desktop", "test-kid");

        assertThrows(Exception.class,
            () -> decoder.decode(token("https://other.test", List.of("parking-desktop"), true)));
        assertThrows(Exception.class,
            () -> decoder.decode(token("https://parking.test", List.of("other-client"), true)));
        assertThrows(Exception.class,
            () -> decoder.decode(token("https://parking.test", List.of("parking-desktop"), false)));
        assertThrows(Exception.class,
            () -> decoder.decode(token("https://parking.test", List.of("parking-desktop"), true, "unknown-kid")));
        assertThrows(Exception.class,
            () -> decoder.decode(token("https://parking.test", List.of("parking-desktop"), true, "test-kid", 901)));
        assertThrows(Exception.class,
            () -> decoder.decode(token("https://parking.test", List.of("parking-desktop"), true,
                "test-kid", 60, java.time.Instant.now().plusSeconds(300))));
    }

    @Test
    void refusesMissingExternalKeyOrIdentityConfiguration() {
        assertThrows(IllegalStateException.class, () -> configuration.jwtDecoder("", "", "", ""));
    }

    @Test
    void signsWithConfiguredExternalKeyAndPublishesConfiguredKid() {
        var configuredEncoder = configuration.desktopJwtEncoder(privatePem(pair), publicPem(pair), "external-test-kid");
        Instant now = Instant.now();
        String token = configuredEncoder.encode(JwtEncoderParameters.from(
            org.springframework.security.oauth2.jwt.JwsHeader.with(SignatureAlgorithm.RS256)
                .keyId("external-test-kid").build(),
            JwtClaimsSet.builder().issuer("https://parking.test").subject("account-17")
                .audience(List.of("parking-desktop")).issuedAt(now).expiresAt(now.plusSeconds(60))
                .id("jti-31").claim("sid", "session-23").claim("ver", 3L).build())).getTokenValue();

        var decoder = configuration.jwtDecoder(publicPem(pair), "https://parking.test", "parking-desktop", "external-test-kid");
        assertEquals("external-test-kid", decoder.decode(token).getHeaders().get("kid"));
    }

    @Test
    void refusesMismatchedExternalSigningAndVerificationKeys() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        KeyPair differentPair = generator.generateKeyPair();

        assertThrows(IllegalStateException.class,
            () -> configuration.desktopJwtEncoder(privatePem(differentPair), publicPem(pair), "wrong-pair-kid"));
    }

    @Test
    void acceptsRetiringKeyOnlyDuringConfiguredOverlapWindow() throws Exception {
        KeyPair oldPair = generateKeyPair();
        Instant verifyUntil = Instant.now().plusSeconds(300);
        String keyring = keyringJson(publicPem(pair), publicPem(oldPair), verifyUntil);
        var decoder = configuration.jwtDecoder(publicPem(pair), "https://parking.test", "parking-desktop",
            "test-kid", keyring, new ObjectMapper());
        String oldToken = tokenWithPair(oldPair, "old-kid", SignatureAlgorithm.RS256,
            Instant.now(), verifyUntil.minusSeconds(30));

        assertEquals("account-17", decoder.decode(oldToken).getSubject());

        String expiredKeyring = keyringJson(publicPem(pair), publicPem(oldPair), Instant.now().minusSeconds(1));
        var expiredDecoder = configuration.jwtDecoder(publicPem(pair), "https://parking.test", "parking-desktop",
            "test-kid", expiredKeyring, new ObjectMapper());
        assertThrows(Exception.class, () -> expiredDecoder.decode(oldToken));
    }

    @Test
    void rejectsBadSignatureAndNonRs256AlgorithmForTrustedKid() throws Exception {
        KeyPair oldPair = generateKeyPair();
        KeyPair attackerPair = generateKeyPair();
        Instant verifyUntil = Instant.now().plusSeconds(300);
        var decoder = configuration.jwtDecoder(publicPem(pair), "https://parking.test", "parking-desktop",
            "test-kid", keyringJson(publicPem(pair), publicPem(oldPair), verifyUntil), new ObjectMapper());

        String forged = tokenWithPair(attackerPair, "old-kid", SignatureAlgorithm.RS256,
            Instant.now(), Instant.now().plusSeconds(60));
        String wrongAlgorithm = tokenWithPair(oldPair, "old-kid", SignatureAlgorithm.RS384,
            Instant.now(), Instant.now().plusSeconds(60));

        assertThrows(Exception.class, () -> decoder.decode(forged));
        assertThrows(Exception.class, () -> decoder.decode(wrongAlgorithm));
    }

    private String token(String issuer, List<String> audience, boolean includeSession) {
        return token(issuer, audience, includeSession, "test-kid");
    }

    private String token(String issuer, List<String> audience, boolean includeSession, String kid) {
        return token(issuer, audience, includeSession, kid, 60);
    }

    private String token(String issuer, List<String> audience, boolean includeSession, String kid, long ttlSeconds) {
        return token(issuer, audience, includeSession, kid, ttlSeconds, Instant.now());
    }

    private String token(String issuer, List<String> audience, boolean includeSession, String kid,
            long ttlSeconds, Instant issuedAt) {
        JwtClaimsSet.Builder claims = JwtClaimsSet.builder()
            .issuer(issuer)
            .subject("account-17")
            .audience(audience)
            .issuedAt(issuedAt)
            .expiresAt(issuedAt.plusSeconds(ttlSeconds))
            .id(UUID.randomUUID().toString())
            .claim("ver", 3L);
        if (includeSession) claims.claim("sid", "session-23");
        NimbusJwtEncoder tokenEncoder = encoder;
        if (!"test-kid".equals(kid)) {
            RSAKey alternateKey = new RSAKey.Builder((java.security.interfaces.RSAPublicKey) pair.getPublic())
                .privateKey((java.security.interfaces.RSAPrivateKey) pair.getPrivate()).keyID(kid).build();
            tokenEncoder = new NimbusJwtEncoder((selector, context) -> selector.select(new JWKSet(alternateKey)));
        }
        return tokenEncoder.encode(JwtEncoderParameters.from(
            org.springframework.security.oauth2.jwt.JwsHeader.with(SignatureAlgorithm.RS256)
                .keyId(kid).type("JWT").build(), claims.build())).getTokenValue();
    }

    private static String publicPem(KeyPair pair) {
        return "-----BEGIN PUBLIC KEY-----\n" +
            java.util.Base64.getMimeEncoder(64, new byte[]{'\n'}).encodeToString(pair.getPublic().getEncoded()) +
            "\n-----END PUBLIC KEY-----";
    }

    private static String privatePem(KeyPair pair) {
        return "-----BEGIN PRIVATE KEY-----\n" +
            java.util.Base64.getMimeEncoder(64, new byte[]{'\n'}).encodeToString(pair.getPrivate().getEncoded()) +
            "\n-----END PRIVATE KEY-----";
    }

    private static KeyPair generateKeyPair() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        return generator.generateKeyPair();
    }

    private static String tokenWithPair(KeyPair pair, String kid, SignatureAlgorithm algorithm,
            Instant issuedAt, Instant expiresAt) {
        RSAKey signingKey = new RSAKey.Builder((java.security.interfaces.RSAPublicKey) pair.getPublic())
            .privateKey((java.security.interfaces.RSAPrivateKey) pair.getPrivate()).keyID(kid).build();
        NimbusJwtEncoder pairEncoder = new NimbusJwtEncoder(
            (selector, context) -> selector.select(new JWKSet(signingKey)));
        return pairEncoder.encode(JwtEncoderParameters.from(
            org.springframework.security.oauth2.jwt.JwsHeader.with(algorithm).keyId(kid).type("JWT").build(),
            JwtClaimsSet.builder().issuer("https://parking.test").subject("account-17")
                .audience(List.of("parking-desktop")).issuedAt(issuedAt).expiresAt(expiresAt)
                .id(UUID.randomUUID().toString()).claim("sid", "session-23").claim("ver", 3L).build()))
            .getTokenValue();
    }

    private static String keyringJson(String activePem, String retiringPem, Instant verifyUntil) {
        return "[{\"kid\":\"test-kid\",\"publicKeyPem\":" + jsonString(activePem) +
            ",\"notBefore\":null,\"verifyUntil\":null},{\"kid\":\"old-kid\",\"publicKeyPem\":" +
            jsonString(retiringPem) + ",\"notBefore\":null,\"verifyUntil\":" +
            jsonString(verifyUntil.toString()) + "}]";
    }

    private static String jsonString(String value) {
        return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"")
            .replace("\r", "\\r").replace("\n", "\\n") + "\"";
    }
}

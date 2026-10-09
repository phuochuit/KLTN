package vn.edu.parking.config;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;
import com.nimbusds.jwt.SignedJWT;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;

import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.Signature;
import java.security.SecureRandom;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Base64;

@Configuration
@ConditionalOnProperty(name = "parking.security.jwt.enabled", havingValue = "true")
public class DesktopJwtConfiguration {
    private static final String REQUIRED_CLAIM_ERROR = "Token is missing required desktop session claims";

    @Bean
    JwtDecoder desktopJwtDecoder(
            @Value("${parking.security.jwt.public-key-pem:}") String publicKeyPem,
            @Value("${parking.security.jwt.issuer:}") String issuer,
            @Value("${parking.security.jwt.audience:}") String audience,
            @Value("${parking.security.jwt.kid:}") String kid,
            @Value("${parking.security.jwt.verification-keys-json:}") String verificationKeysJson,
            ObjectMapper objectMapper) {
        return jwtDecoder(publicKeyPem, issuer, audience, kid, verificationKeysJson, objectMapper);
    }

    @Bean
    JwtEncoder desktopJwtEncoder(
            @Value("${parking.security.jwt.private-key-pem:}") String privateKeyPem,
            @Value("${parking.security.jwt.public-key-pem:}") String publicKeyPem,
            @Value("${parking.security.jwt.kid:}") String kid) {
        requireConfigured(kid, "parking.security.jwt.kid");
        try {
            RSAPublicKey publicKey = parsePublicKey(publicKeyPem);
            RSAPrivateKey privateKey = parsePrivateKey(privateKeyPem);
            verifyKeyPair(publicKey, privateKey);
            RSAKey rsaKey = new RSAKey.Builder(publicKey).privateKey(privateKey).keyID(kid).algorithm(JWSAlgorithm.RS256).build();
            JWKSource<SecurityContext> source = (selector, context) -> selector.select(new JWKSet(rsaKey));
            return new NimbusJwtEncoder(source);
        } catch (Exception ex) {
            throw new IllegalStateException("Invalid externally provisioned desktop JWT signing key", ex);
        }
    }

    JwtDecoder jwtDecoder(String publicKeyPem, String issuer, String audience, String kid) {
        return jwtDecoder(publicKeyPem, issuer, audience, kid, "", new ObjectMapper());
    }

    JwtDecoder jwtDecoder(String activePublicKeyPem, String issuer, String audience, String activeKid,
            String verificationKeysJson, ObjectMapper objectMapper) {
        requireConfigured(issuer, "parking.security.jwt.issuer");
        requireConfigured(audience, "parking.security.jwt.audience");
        requireConfigured(activeKid, "parking.security.jwt.kid");
        try {
            RSAPublicKey activePublicKey = parsePublicKey(activePublicKeyPem);
            Map<String, VerificationKey> keyring = parseVerificationKeyring(
                verificationKeysJson, activeKid, activePublicKey, objectMapper);
            Map<String, JwtDecoder> decoders = new LinkedHashMap<>();
            for (Map.Entry<String, VerificationKey> entry : keyring.entrySet())
                decoders.put(entry.getKey(), createKeyDecoder(entry.getValue(), issuer, audience));

            return token -> {
                try {
                    var header = SignedJWT.parse(token).getHeader();
                    if (!JWSAlgorithm.RS256.equals(header.getAlgorithm()))
                        throw new BadJwtException("Unsupported signing algorithm");
                    String tokenKid = header.getKeyID();
                    JwtDecoder selected = decoders.get(tokenKid);
                    VerificationKey key = keyring.get(tokenKid);
                    if (selected == null || key == null)
                        throw new BadJwtException("Unknown signing key id");
                    Instant now = Instant.now();
                    if ((key.notBefore() != null && now.isBefore(key.notBefore()))
                            || (key.verifyUntil() != null && !now.isBefore(key.verifyUntil())))
                        throw new BadJwtException("Signing key is outside its verification window");
                    return selected.decode(token);
                } catch (BadJwtException ex) {
                    throw ex;
                } catch (Exception ex) {
                    throw new BadJwtException("Invalid desktop access token", ex);
                }
            };
        } catch (Exception ex) {
            if (ex instanceof IllegalStateException stateException) throw stateException;
            throw new IllegalStateException("Invalid externally provisioned desktop JWT verification key", ex);
        }
    }

    private static NimbusJwtDecoder createKeyDecoder(VerificationKey key, String issuer, String audience) throws Exception {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withPublicKey(key.publicKey())
            .signatureAlgorithm(SignatureAlgorithm.RS256)
            .build();
        OAuth2TokenValidator<Jwt> issuerAndTime = JwtValidators.createDefaultWithIssuer(issuer);
        OAuth2TokenValidator<Jwt> audienceValidator = new JwtClaimValidator<List<String>>(
            "aud", values -> values != null && values.contains(audience));
        OAuth2TokenValidator<Jwt> keyWindowValidator = jwt -> {
            Instant issuedAt = jwt.getIssuedAt();
            Instant expiresAt = jwt.getExpiresAt();
            Instant now = Instant.now();
            boolean withinWindow = issuedAt != null && expiresAt != null
                && (key.notBefore() == null || (!now.isBefore(key.notBefore()) && !issuedAt.isBefore(key.notBefore())))
                && (key.verifyUntil() == null || (now.isBefore(key.verifyUntil())
                    && issuedAt.isBefore(key.verifyUntil()) && !expiresAt.isAfter(key.verifyUntil())));
            return withinWindow ? OAuth2TokenValidatorResult.success()
                : OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token", "Token is outside signing-key validity window", null));
        };
        OAuth2TokenValidator<Jwt> sessionClaims = jwt -> {
            Object version = jwt.getClaims().get("ver");
            Instant issuedAt = jwt.getIssuedAt();
            Instant expiresAt = jwt.getExpiresAt();
            boolean valid = hasText(jwt.getSubject())
                && hasText(jwt.getId())
                && hasText(jwt.getClaimAsString("sid"))
                && version instanceof Number number
                && number.longValue() >= 0
                && issuedAt != null
                && !issuedAt.isAfter(Instant.now().plusSeconds(60))
                && expiresAt != null
                && expiresAt.isAfter(issuedAt)
                && Duration.between(issuedAt, expiresAt).compareTo(Duration.ofMinutes(15)) <= 0;
            return valid ? OAuth2TokenValidatorResult.success()
                : OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token", REQUIRED_CLAIM_ERROR, null));
        };
        decoder.setJwtValidator(jwt -> {
            OAuth2TokenValidatorResult result = issuerAndTime.validate(jwt);
            if (result.hasErrors()) return result;
            result = audienceValidator.validate(jwt);
            if (result.hasErrors()) return result;
            result = keyWindowValidator.validate(jwt);
            if (result.hasErrors()) return result;
            return sessionClaims.validate(jwt);
        });
        return decoder;
    }

    private static Map<String, VerificationKey> parseVerificationKeyring(String json, String activeKid,
            RSAPublicKey activePublicKey, ObjectMapper objectMapper) throws Exception {
        Map<String, VerificationKey> keyring = new LinkedHashMap<>();
        if (json == null || json.isBlank()) {
            keyring.put(activeKid, new VerificationKey(activeKid, activePublicKey, null, null));
            return keyring;
        }
        List<VerificationKeyConfig> entries;
        try {
            entries = objectMapper.readValue(json, objectMapper.getTypeFactory()
                .constructCollectionType(List.class, VerificationKeyConfig.class));
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Invalid external JWT verification keyring JSON", ex);
        }
        if (entries == null || entries.isEmpty())
            throw new IllegalStateException("External JWT verification keyring must not be empty");
        for (VerificationKeyConfig entry : entries) {
            if (entry == null || !hasText(entry.kid()) || !hasText(entry.publicKeyPem()))
                throw new IllegalStateException("Every JWT verification key needs kid and publicKeyPem");
            Instant notBefore = parseInstant(entry.notBefore(), "notBefore");
            Instant verifyUntil = parseInstant(entry.verifyUntil(), "verifyUntil");
            boolean active = activeKid.equals(entry.kid());
            if (active && (notBefore != null || verifyUntil != null))
                throw new IllegalStateException("Active JWT signing key must not have a retirement window");
            if (!active && verifyUntil == null)
                throw new IllegalStateException("Retiring JWT verification keys must have verifyUntil");
            if (notBefore != null && verifyUntil != null && !notBefore.isBefore(verifyUntil))
                throw new IllegalStateException("JWT key notBefore must precede verifyUntil");
            RSAPublicKey publicKey = parsePublicKey(entry.publicKeyPem());
            if (active && (!activePublicKey.getModulus().equals(publicKey.getModulus())
                    || !activePublicKey.getPublicExponent().equals(publicKey.getPublicExponent())))
                throw new IllegalStateException("Active keyring public key does not match configured signing key");
            VerificationKey previous = keyring.putIfAbsent(entry.kid(),
                new VerificationKey(entry.kid(), publicKey, notBefore, verifyUntil));
            if (previous != null) throw new IllegalStateException("Duplicate kid in JWT verification keyring");
        }
        if (!keyring.containsKey(activeKid))
            throw new IllegalStateException("JWT verification keyring must contain the active kid");
        return keyring;
    }

    private static Instant parseInstant(String value, String name) {
        if (value == null || value.isBlank()) return null;
        try {
            return Instant.parse(value);
        } catch (java.time.format.DateTimeParseException ex) {
            throw new IllegalStateException("Invalid JWT keyring " + name + " timestamp", ex);
        }
    }

    private record VerificationKey(String kid, RSAPublicKey publicKey, Instant notBefore, Instant verifyUntil) { }
    public record VerificationKeyConfig(String kid, String publicKeyPem, String notBefore, String verifyUntil) { }

    private static RSAPublicKey parsePublicKey(String pem) throws Exception {
        byte[] der = decodePem(pem, "PUBLIC KEY", "parking.security.jwt.public-key-pem");
        RSAPublicKey key = (RSAPublicKey) KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(der));
        if (key.getModulus().bitLength() < 2048) throw new IllegalStateException("RSA key must be at least 2048 bits");
        return key;
    }

    private static RSAPrivateKey parsePrivateKey(String pem) throws Exception {
        byte[] der = decodePem(pem, "PRIVATE KEY", "parking.security.jwt.private-key-pem");
        RSAPrivateKey key = (RSAPrivateKey) KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(der));
        if (key.getModulus().bitLength() < 2048) throw new IllegalStateException("RSA key must be at least 2048 bits");
        return key;
    }

    private static void verifyKeyPair(RSAPublicKey publicKey, RSAPrivateKey privateKey) throws Exception {
        byte[] challenge = new byte[32];
        new SecureRandom().nextBytes(challenge);
        Signature signer = Signature.getInstance("SHA256withRSA");
        signer.initSign(privateKey);
        signer.update(challenge);
        byte[] signature = signer.sign();
        Signature verifier = Signature.getInstance("SHA256withRSA");
        verifier.initVerify(publicKey);
        verifier.update(challenge);
        if (!verifier.verify(signature)) {
            throw new IllegalStateException("External desktop JWT signing and verification keys do not match");
        }
    }

    private static byte[] decodePem(String pem, String label, String property) {
        requireConfigured(pem, property);
        String begin = "-----BEGIN " + label + "-----";
        String end = "-----END " + label + "-----";
        String normalized = pem.replace("\\n", "\n").trim();
        if (!normalized.startsWith(begin) || !normalized.endsWith(end)) {
            throw new IllegalStateException("Expected PKCS#8/X.509 PEM value for " + property);
        }
        String body = normalized.substring(begin.length(), normalized.length() - end.length())
            .replaceAll("\\s", "");
        return Base64.getDecoder().decode(body.getBytes(StandardCharsets.US_ASCII));
    }

    private static void requireConfigured(String value, String property) {
        if (!hasText(value)) throw new IllegalStateException("Required external configuration is missing: " + property);
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}

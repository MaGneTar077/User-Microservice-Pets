package user.microservice.pets.infrastructure.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.UnsupportedJwtException;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.util.Date;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("JwtUtil - RS256 + legacy HS256 - Unit Tests")
class JwtUtilTest {

    private static final String LEGACY_SECRET = "legacy-secret-at-least-32-bytes-long!!";
    private static final String ISSUER = "myanimalog-user-service";
    private static final String AUDIENCE = "myanimalog-api";

    private JwtKeyProvider jwtKeyProvider;

    @BeforeEach
    void setUp() throws Exception {
        String pem = TestRsaKeys.generatePrivateKeyPem();
        jwtKeyProvider = new JwtKeyProvider(pem, "test-key-1", new MockEnvironment());
    }

    private JwtUtil newJwtUtil(long ttlMinutes, boolean acceptLegacy) {
        return new JwtUtil(LEGACY_SECRET, ttlMinutes, acceptLegacy, ISSUER, AUDIENCE, jwtKeyProvider);
    }

    @Test
    @DisplayName("Generated token is signed RS256 with the 'kid' header and validates with the public key alone")
    void shouldIssueRs256TokenValidatableWithPublicKeyAlone() {
        JwtUtil jwtUtil = newJwtUtil(60, true);
        String userId = "8f1c2e3a-0000-0000-0000-000000000001";

        String token = jwtUtil.generateToken(userId, Map.of(
                "id", userId,
                "email", "ana@correo.com",
                "username", "ana123",
                "provider", "LOCAL"
        ));

        // Simula "pegar la llave publica en jwt.io": se valida SOLO con la llave publica, sin pasar por JwtUtil.
        Claims claimsFromPublicKeyOnly = Jwts.parserBuilder()
                .setSigningKey(jwtKeyProvider.getPublicKey())
                .build()
                .parseClaimsJws(token)
                .getBody();

        assertThat(claimsFromPublicKeyOnly.getSubject()).isEqualTo(userId);
        assertThat(claimsFromPublicKeyOnly.get("id")).isEqualTo(userId);
        assertThat(claimsFromPublicKeyOnly.get("email")).isEqualTo("ana@correo.com");
        assertThat(claimsFromPublicKeyOnly.get("username")).isEqualTo("ana123");
        assertThat(claimsFromPublicKeyOnly.get("provider")).isEqualTo("LOCAL");
        assertThat(claimsFromPublicKeyOnly.getIssuer()).isEqualTo(ISSUER);
        assertThat(claimsFromPublicKeyOnly.getAudience()).isEqualTo(AUDIENCE);
        assertThat(claimsFromPublicKeyOnly.getId()).isNotBlank();
        assertThat(claimsFromPublicKeyOnly.getIssuedAt()).isNotNull();
        assertThat(claimsFromPublicKeyOnly.getExpiration()).isAfter(new Date());

        // JwtUtil.validateToken debe aceptar el mismo token a traves de su propio resolver.
        Claims claims = jwtUtil.validateToken(token);
        assertThat(claims.getSubject()).isEqualTo(userId);
    }

    @Test
    @DisplayName("Two tokens for the same user should carry different 'jti' values")
    void shouldGenerateUniqueJtiPerToken() {
        JwtUtil jwtUtil = newJwtUtil(60, true);

        String first = jwtUtil.validateToken(jwtUtil.generateToken("user-id", Map.of())).getId();
        String second = jwtUtil.validateToken(jwtUtil.generateToken("user-id", Map.of())).getId();

        assertThat(first).isNotBlank();
        assertThat(second).isNotBlank();
        assertThat(first).isNotEqualTo(second);
    }

    @Test
    @DisplayName("Should reject a new-style (RS256 + kid) token with the wrong issuer")
    void shouldRejectNewTokenWithWrongIssuer() {
        JwtUtil jwtUtil = newJwtUtil(60, true);
        String token = buildNewStyleToken("some-other-issuer", AUDIENCE);

        assertThatThrownBy(() -> jwtUtil.validateToken(token))
                .isInstanceOf(JwtException.class);
    }

    @Test
    @DisplayName("Should reject a new-style (RS256 + kid) token with the wrong audience")
    void shouldRejectNewTokenWithWrongAudience() {
        JwtUtil jwtUtil = newJwtUtil(60, true);
        String token = buildNewStyleToken(ISSUER, "some-other-api");

        assertThatThrownBy(() -> jwtUtil.validateToken(token))
                .isInstanceOf(JwtException.class);
    }

    @Test
    @DisplayName("Legacy tokens without iss/aud should still be accepted (the iss/aud check only applies to new tokens)")
    void shouldAcceptLegacyTokenEvenWithoutIssuerOrAudience() {
        JwtUtil jwtUtil = newJwtUtil(60, true);
        String legacyToken = buildLegacyHs256Token("user@test.com");

        Claims claims = jwtUtil.validateToken(legacyToken);

        assertThat(claims.getIssuer()).isNull();
        assertThat(claims.getAudience()).isNull();
    }

    @Test
    @DisplayName("Should keep accepting legacy HS256 tokens (no 'kid') while JWT_ACCEPT_LEGACY=true")
    void shouldAcceptLegacyHs256TokenWhenAcceptLegacyIsTrue() {
        JwtUtil jwtUtil = newJwtUtil(60, true);
        String legacyToken = buildLegacyHs256Token("user@test.com");

        Claims claims = jwtUtil.validateToken(legacyToken);

        assertThat(claims.getSubject()).isEqualTo("user@test.com");
        assertThat(claims.get("id")).isEqualTo("legacy-id-123");
    }

    @Test
    @DisplayName("Should reject legacy HS256 tokens once JWT_ACCEPT_LEGACY=false")
    void shouldRejectLegacyHs256TokenWhenAcceptLegacyIsFalse() {
        JwtUtil jwtUtil = newJwtUtil(60, false);
        String legacyToken = buildLegacyHs256Token("user@test.com");

        assertThatThrownBy(() -> jwtUtil.validateToken(legacyToken))
                .isInstanceOf(UnsupportedJwtException.class);
    }

    @Test
    @DisplayName("Access token TTL should come from JWT_ACCESS_TTL_MINUTES")
    void shouldHonorConfiguredTtl() {
        JwtUtil jwtUtil = newJwtUtil(1, true);
        String token = jwtUtil.generateToken("user-id", Map.of("id", "user-id"));

        Date expiration = jwtUtil.extractExpiration(token);

        long ttlMs = expiration.getTime() - System.currentTimeMillis();
        assertThat(ttlMs).isPositive().isLessThanOrEqualTo(60_000L);
    }

    private String buildNewStyleToken(String issuer, String audience) {
        return Jwts.builder()
                .setHeaderParam("kid", jwtKeyProvider.getKeyId())
                .setIssuer(issuer)
                .setAudience(audience)
                .setId(java.util.UUID.randomUUID().toString())
                .setSubject("user-id")
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + 3_600_000))
                .signWith(jwtKeyProvider.getPrivateKey(), SignatureAlgorithm.RS256)
                .compact();
    }

    private String buildLegacyHs256Token(String subjectEmail) {
        Key legacyKey = Keys.hmacShaKeyFor(LEGACY_SECRET.getBytes(StandardCharsets.UTF_8));
        return Jwts.builder()
                .setSubject(subjectEmail)
                .addClaims(Map.of("id", "legacy-id-123", "email", subjectEmail))
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + 3_600_000))
                .signWith(legacyKey)
                .compact();
    }
}

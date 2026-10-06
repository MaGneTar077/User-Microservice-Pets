package user.microservice.pets.infrastructure.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.util.Date;
import java.util.Map;
import java.util.UUID;

@Component
public class JwtUtil {

    private final Key legacySigningKey;
    private final RSAPrivateKey rsaPrivateKey;
    private final String keyId;
    private final long expirationMillis;
    private final String issuer;
    private final String audience;
    private final JwtSigningKeyResolver signingKeyResolver;

    public JwtUtil(
            @Value("${jwt.secret}") String legacySecret,
            @Value("${jwt.access-ttl-minutes:60}") long accessTtlMinutes,
            @Value("${jwt.accept-legacy:true}") boolean acceptLegacy,
            @Value("${jwt.issuer:myanimalog-user-service}") String issuer,
            @Value("${jwt.audience:myanimalog-api}") String audience,
            JwtKeyProvider jwtKeyProvider) {
        this.legacySigningKey = Keys.hmacShaKeyFor(legacySecret.getBytes(StandardCharsets.UTF_8));
        this.rsaPrivateKey = jwtKeyProvider.getPrivateKey();
        this.keyId = jwtKeyProvider.getKeyId();
        this.expirationMillis = accessTtlMinutes * 60 * 1000;
        this.issuer = issuer;
        this.audience = audience;

        RSAPublicKey rsaPublicKey = jwtKeyProvider.getPublicKey();
        this.signingKeyResolver = new JwtSigningKeyResolver(rsaPublicKey, legacySigningKey, acceptLegacy);
    }

    public String generateToken(String subject, Map<String, Object> claims) {
        return Jwts.builder()
                .setHeaderParam("kid", keyId)
                .setIssuer(issuer)
                .setAudience(audience)
                .setId(UUID.randomUUID().toString())
                .setSubject(subject)
                .addClaims(claims)
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + expirationMillis))
                .signWith(rsaPrivateKey, SignatureAlgorithm.RS256)
                .compact();
    }

    public Claims validateToken(String token) {
        Jws<Claims> jws = Jwts.parserBuilder()
                .setSigningKeyResolver(signingKeyResolver)
                .build()
                .parseClaimsJws(token);

        Claims claims = jws.getBody();

        // Los tokens nuevos (RS256, con "kid") deben declarar iss/aud del contrato compartido.
        // Los tokens legados HS256 no tienen esos claims, asi que la verificacion no les aplica.
        boolean isNewToken = jws.getHeader().getKeyId() != null;
        if (isNewToken) {
            if (!issuer.equals(claims.getIssuer())) {
                throw new JwtException("Invalid token issuer");
            }
            if (claims.getAudience() == null || !claims.getAudience().contains(audience)) {
                throw new JwtException("Invalid token audience");
            }
        }

        return claims;
    }

    public Date extractExpiration(String token) {
        return validateToken(token).getExpiration();
    }

    public long getAccessTokenTtlSeconds() {
        return expirationMillis / 1000;
    }
}

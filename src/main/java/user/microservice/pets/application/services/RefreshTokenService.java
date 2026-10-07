package user.microservice.pets.application.services;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import user.microservice.pets.domain.enums.TokenContext;
import user.microservice.pets.domain.exceptions.InvalidTokenException;
import user.microservice.pets.domain.model.RefreshToken;
import user.microservice.pets.domain.ports.out.RefreshTokenRepositoryPort;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.UUID;

/**
 * Refresh tokens opacos (no JWT): CONTRATOS_COMPARTIDOS.md §1.4.
 * Se guardan solo como hash SHA-256 (nunca el valor plano) y rotan en cada uso.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RefreshTokenService {

    private static final int VALIDITY_DAYS = 30;

    private final RefreshTokenRepositoryPort refreshTokenRepository;

    public String issue(UUID userId) {
        return issue(userId, TokenContext.USER, null);
    }

    public String issueVeterinaryContext(UUID userId, UUID veterinaryId) {
        return issue(userId, TokenContext.VETERINARY, veterinaryId);
    }

    private String issue(UUID userId, TokenContext context, UUID veterinaryId) {
        String rawToken = generateRawToken();

        RefreshToken token = RefreshToken.builder()
                .id(UUID.randomUUID())
                .userId(userId)
                .tokenHash(hash(rawToken))
                .context(context)
                .veterinaryId(veterinaryId)
                .expiresAt(LocalDateTime.now().plusDays(VALIDITY_DAYS))
                .createdAt(LocalDateTime.now())
                .build();

        refreshTokenRepository.save(token);
        return rawToken;
    }

    /**
     * Busca y valida un refresh token (firma = existe, no revocado, no expirado) sin rotarlo
     * todavia. Separado de rotate() porque USER-04 necesita consultar la membresia de Veterinary
     * ANTES de decidir si rota o revoca.
     */
    public RefreshToken validate(String rawToken) {
        RefreshToken existing = refreshTokenRepository.findByTokenHash(hash(rawToken))
                .orElseThrow(() -> new InvalidTokenException("Invalid refresh token"));

        if (existing.getRevokedAt() != null) {
            log.warn("Reuse of an already-revoked refresh token for user {}; revoking all active sessions",
                    existing.getUserId());
            refreshTokenRepository.revokeAllActiveForUser(existing.getUserId());
            throw new InvalidTokenException("Refresh token has already been used");
        }

        if (existing.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new InvalidTokenException("Refresh token expired");
        }

        return existing;
    }

    public RotatedRefreshToken rotate(String rawToken) {
        return rotate(validate(rawToken));
    }

    public RotatedRefreshToken rotate(RefreshToken existing) {
        String newRawToken = generateRawToken();
        RefreshToken newToken = RefreshToken.builder()
                .id(UUID.randomUUID())
                .userId(existing.getUserId())
                .tokenHash(hash(newRawToken))
                .context(existing.getContext())
                .veterinaryId(existing.getVeterinaryId())
                .expiresAt(LocalDateTime.now().plusDays(VALIDITY_DAYS))
                .createdAt(LocalDateTime.now())
                .build();
        refreshTokenRepository.save(newToken);

        existing.setRevokedAt(LocalDateTime.now());
        existing.setReplacedBy(newToken.getId());
        refreshTokenRepository.save(existing);

        return new RotatedRefreshToken(existing.getUserId(), newRawToken, existing.getContext(), existing.getVeterinaryId());
    }

    public void revoke(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            return;
        }
        refreshTokenRepository.findByTokenHash(hash(rawToken))
                .filter(token -> token.getRevokedAt() == null)
                .ifPresent(this::revoke);
    }

    public void revoke(RefreshToken existing) {
        existing.setRevokedAt(LocalDateTime.now());
        refreshTokenRepository.save(existing);
    }

    private String generateRawToken() {
        byte[] randomBytes = new byte[32];
        new SecureRandom().nextBytes(randomBytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);
    }

    private String hash(String rawToken) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(rawToken.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(digest.length * 2);
            for (byte b : digest) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 no esta disponible en esta JVM", e);
        }
    }

    public record RotatedRefreshToken(UUID userId, String rawToken, TokenContext context, UUID veterinaryId) {
    }
}

package user.microservice.pets.domain.ports.out;

import user.microservice.pets.domain.model.RefreshToken;

import java.util.Optional;
import java.util.UUID;

public interface RefreshTokenRepositoryPort {
    RefreshToken save(RefreshToken refreshToken);
    Optional<RefreshToken> findByTokenHash(String tokenHash);
    void revokeAllActiveForUser(UUID userId);
}

package user.microservice.pets.infrastructure.adapters;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import user.microservice.pets.domain.model.RefreshToken;
import user.microservice.pets.domain.ports.out.RefreshTokenRepositoryPort;
import user.microservice.pets.infrastructure.entity.RefreshTokenEntity;
import user.microservice.pets.infrastructure.repositories.JpaRefreshTokenRepository;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class RefreshTokenRepositoryAdapter implements RefreshTokenRepositoryPort {

    private final JpaRefreshTokenRepository jpaRefreshTokenRepository;

    @Override
    public RefreshToken save(RefreshToken refreshToken) {
        RefreshTokenEntity saved = jpaRefreshTokenRepository.save(toEntity(refreshToken));
        return toDomainModel(saved);
    }

    @Override
    public Optional<RefreshToken> findByTokenHash(String tokenHash) {
        return jpaRefreshTokenRepository.findByTokenHash(tokenHash)
                .map(this::toDomainModel);
    }

    @Override
    @Transactional
    public void revokeAllActiveForUser(UUID userId) {
        jpaRefreshTokenRepository.revokeAllActiveForUser(userId, LocalDateTime.now());
    }

    private RefreshToken toDomainModel(RefreshTokenEntity entity) {
        return RefreshToken.builder()
                .id(entity.getId())
                .userId(entity.getUserId())
                .tokenHash(entity.getTokenHash())
                .context(entity.getContext())
                .veterinaryId(entity.getVeterinaryId())
                .expiresAt(entity.getExpiresAt())
                .revokedAt(entity.getRevokedAt())
                .replacedBy(entity.getReplacedBy())
                .createdAt(entity.getCreatedAt())
                .build();
    }

    private RefreshTokenEntity toEntity(RefreshToken domain) {
        return RefreshTokenEntity.builder()
                .id(domain.getId())
                .userId(domain.getUserId())
                .tokenHash(domain.getTokenHash())
                .context(domain.getContext())
                .veterinaryId(domain.getVeterinaryId())
                .expiresAt(domain.getExpiresAt())
                .revokedAt(domain.getRevokedAt())
                .replacedBy(domain.getReplacedBy())
                .createdAt(domain.getCreatedAt())
                .build();
    }
}

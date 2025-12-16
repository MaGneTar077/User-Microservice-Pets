package user.microservice.pets.infrastructure.adapters;

import org.springframework.stereotype.Component;
import user.microservice.pets.domain.model.PasswordResetToken;
import user.microservice.pets.domain.ports.out.PasswordResetTokenRepositoryPort;
import user.microservice.pets.infrastructure.entity.PasswordResetTokenEntity;
import user.microservice.pets.infrastructure.repositories.JpaPasswordResetTokenRepository;

import java.time.LocalDateTime;
import java.util.Optional;

@Component
public class PasswordResetTokenRepositoryAdapter implements PasswordResetTokenRepositoryPort {

    private final JpaPasswordResetTokenRepository jpaRepository;

    public PasswordResetTokenRepositoryAdapter(JpaPasswordResetTokenRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public PasswordResetToken save(PasswordResetToken token) {
        PasswordResetTokenEntity entity = new PasswordResetTokenEntity();
        entity.setEmail(token.getEmail());
        entity.setToken(token.getToken());
        entity.setExpiresAt(token.getExpiresAt());
        entity.setCreatedAt(token.getCreatedAt() != null ? token.getCreatedAt() : LocalDateTime.now());
        entity.setUsed(token.getUsed() != null ? token.getUsed() : false);

        PasswordResetTokenEntity saved = jpaRepository.save(entity);

        return new PasswordResetToken(
                saved.getId(),
                saved.getToken(),
                saved.getEmail(),
                saved.getExpiresAt(),
                saved.getCreatedAt(),
                saved.getUsed()
        );
    }

    @Override
    public Optional<PasswordResetToken> findByToken(String token) {
        return jpaRepository.findByToken(token)
                .map(e -> new PasswordResetToken(
                        e.getId(),
                        e.getToken(),
                        e.getEmail(),
                        e.getExpiresAt(),
                        e.getCreatedAt(),
                        e.getUsed()
                ));
    }

    @Override
    public void deleteByToken(String token) {
        jpaRepository.deleteByToken(token);
    }

    @Override
    public void deleteByEmail(String email) {
        jpaRepository.deleteByEmail(email);
    }

    @Override
    public void deleteAllExpiredTokens() {
        jpaRepository.deleteAllByExpiresAtBefore(LocalDateTime.now());
    }
}

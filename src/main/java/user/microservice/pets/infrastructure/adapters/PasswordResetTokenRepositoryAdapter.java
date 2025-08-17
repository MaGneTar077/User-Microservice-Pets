package user.microservice.pets.infrastructure.adapters;

import org.springframework.stereotype.Component;
import user.microservice.pets.domain.model.PasswordResetToken;
import user.microservice.pets.domain.ports.out.PasswordResetTokenRepositoryPort;
import user.microservice.pets.infrastructure.entity.PasswordResetTokenEntity;
import user.microservice.pets.infrastructure.repositories.JpaPasswordResetTokenRepository;


import java.time.LocalDateTime;
import java.util.Optional;

@Component
public class PasswordResetTokenRepositoryAdapter implements PasswordResetTokenRepositoryPort{

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
        PasswordResetTokenEntity saved = jpaRepository.save(entity);
        return new PasswordResetToken(saved.getToken(), saved.getEmail(), saved.getExpiresAt());
    }

    @Override
    public Optional<PasswordResetToken> findByToken(String token) {
        return jpaRepository.findByToken(token)
                .map(e -> new PasswordResetToken(e.getToken(), e.getEmail(), e.getExpiresAt()));
    }

    @Override
    public void deleteByToken(String token) {
        jpaRepository.deleteByToken(token);
    }

    @Override
    public void deleteAllExpiredTokens() {
        jpaRepository.deleteAllByExpiresAtBefore(LocalDateTime.now());
    }
}

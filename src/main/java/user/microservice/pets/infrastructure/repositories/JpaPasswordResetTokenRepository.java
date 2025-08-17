package user.microservice.pets.infrastructure.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import user.microservice.pets.infrastructure.entity.PasswordResetTokenEntity;

import java.util.Optional;
import java.time.LocalDateTime;

public interface JpaPasswordResetTokenRepository extends JpaRepository<PasswordResetTokenEntity, Long> {

    Optional<PasswordResetTokenEntity> findByToken(String token);
    void deleteByToken(String token);
    void deleteAllByExpiresAtBefore(LocalDateTime dateTime);
}

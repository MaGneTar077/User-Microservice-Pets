package user.microservice.pets.infrastructure.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.transaction.annotation.Transactional;
import user.microservice.pets.infrastructure.entity.PasswordResetTokenEntity;

import java.util.Optional;
import java.time.LocalDateTime;

public interface JpaPasswordResetTokenRepository extends JpaRepository<PasswordResetTokenEntity, Long> {

    Optional<PasswordResetTokenEntity> findByToken(String token);

    @Transactional
    @Modifying
    void deleteByToken(String token);

    @Transactional
    @Modifying
    void deleteByEmail(String email);

    @Transactional
    @Modifying
    void deleteAllByExpiresAtBefore(LocalDateTime dateTime);
}

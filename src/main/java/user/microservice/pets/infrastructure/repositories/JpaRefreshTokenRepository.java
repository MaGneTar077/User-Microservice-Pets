package user.microservice.pets.infrastructure.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import user.microservice.pets.infrastructure.entity.RefreshTokenEntity;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

public interface JpaRefreshTokenRepository extends JpaRepository<RefreshTokenEntity, UUID> {

    Optional<RefreshTokenEntity> findByTokenHash(String tokenHash);

    @Modifying
    @Query("UPDATE RefreshTokenEntity r SET r.revokedAt = :now WHERE r.userId = :userId AND r.revokedAt IS NULL")
    void revokeAllActiveForUser(@Param("userId") UUID userId, @Param("now") LocalDateTime now);
}

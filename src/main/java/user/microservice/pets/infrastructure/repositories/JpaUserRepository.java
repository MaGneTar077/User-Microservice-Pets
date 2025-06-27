package user.microservice.pets.infrastructure.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import user.microservice.pets.infrastructure.entity.UserEntity;

import java.util.Optional;
import java.util.UUID;

public interface JpaUserRepository extends JpaRepository<UserEntity, UUID>{
    Optional<UserEntity> findByEmail(String email);
    boolean existsByEmail(String email);
}

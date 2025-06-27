package user.microservice.pets.infraestructure.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import user.microservice.pets.infraestructure.entity.UserEntity;

import java.util.Optional;
import java.util.UUID;

public interface UserJpaRepository extends JpaRepository<UserEntity, UUID> {
    Optional<UserEntity> findByEmail(String email);
}

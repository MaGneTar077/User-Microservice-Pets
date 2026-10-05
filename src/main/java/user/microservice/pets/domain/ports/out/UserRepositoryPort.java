package user.microservice.pets.domain.ports.out;

import user.microservice.pets.domain.model.User;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserRepositoryPort {
    Optional<User> findByEmail(String email);
    Optional<User> findById(UUID id);
    List<User> findAllByIds(List<UUID> ids);
    boolean existsByEmail(String email);
    boolean existsByUsername(String username);
    User save(User user);
    User updateProfileImage(UUID userId, String imageUrl);
}

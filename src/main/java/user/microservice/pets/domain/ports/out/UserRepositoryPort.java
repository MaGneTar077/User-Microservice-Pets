package user.microservice.pets.domain.ports.out;

import user.microservice.pets.domain.model.User;

import java.util.Optional;

public interface UserRepositoryPort {
    Optional<User> findByEmail(String email);
    boolean existsByEmail(String email);
    User save(User user);
}

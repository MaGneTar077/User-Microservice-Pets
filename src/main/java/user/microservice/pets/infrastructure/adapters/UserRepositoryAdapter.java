package user.microservice.pets.infrastructure.adapters;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import user.microservice.pets.domain.ports.out.UserRepositoryPort;
import user.microservice.pets.domain.model.User;
import user.microservice.pets.infrastructure.entity.UserEntity;
import user.microservice.pets.infrastructure.repositories.JpaUserRepository;

import java.util.Optional;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class UserRepositoryAdapter implements UserRepositoryPort {

    private final JpaUserRepository jpaUserRepository;

    @Override
    public Optional<User> findByEmail(String email) {
        return jpaUserRepository.findByEmail(email)
                .map(this::toDomainModel);
    }

    @Override
    public Optional<User> findById(UUID id) {
        return jpaUserRepository.findById(id)
                .map(this::toDomainModel);
    }

    @Override
    public boolean existsByEmail(String email) {
        return jpaUserRepository.existsByEmail(email);
    }

    @Override
    public boolean existsByUsername(String username) {
        return jpaUserRepository.existsByUsername(username);
    }

    @Override
    public User save(User user) {
        UserEntity entity = toEntity(user);
        return toDomainModel(jpaUserRepository.save(entity));
    }

    @Override
    public User updateProfileImage(UUID userId, String imageUrl) {
        UserEntity entity = jpaUserRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));

        entity.setProfileImageUrl(imageUrl);
        UserEntity saved = jpaUserRepository.save(entity);

        return toDomainModel(saved);
    }

    private User toDomainModel(UserEntity entity) {
        return User.builder()
                .id(entity.getId())
                .username(entity.getUsername())
                .email(entity.getEmail())
                .password(entity.getPassword())
                .profileImageUrl(entity.getProfileImageUrl())
                .createdAt(entity.getCreatedAt())
                .authProvider(entity.getAuthProvider())
                .build();
    }

    private UserEntity toEntity(User domain) {
        return UserEntity.builder()
                .id(domain.getId())
                .username(domain.getUsername())
                .email(domain.getEmail())
                .password(domain.getPassword())
                .profileImageUrl(domain.getProfileImageUrl())
                .createdAt(domain.getCreatedAt())
                .authProvider(domain.getAuthProvider())
                .build();
    }
}
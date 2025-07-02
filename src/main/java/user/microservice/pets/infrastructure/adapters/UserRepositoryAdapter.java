package user.microservice.pets.infrastructure.adapters;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import user.microservice.pets.domain.ports.out.UserRepositoryPort;
import user.microservice.pets.domain.model.User;
import user.microservice.pets.infrastructure.entity.UserEntity;
import user.microservice.pets.infrastructure.repositories.JpaUserRepository;

import java.util.Optional;

@Component
@RequiredArgsConstructor
public class UserRepositoryAdapter implements UserRepositoryPort {

    private final JpaUserRepository jpaUserRepository;

    @Override
    public Optional<User> findByEmail(String email){
        return jpaUserRepository.findByEmail(email)
                .map(this::toDomainModel);
    }

    @Override
    public boolean existsByEmail(String email){
        return jpaUserRepository.existsByEmail(email);
    }

    @Override
    public User save(User user){
        UserEntity entity = toEntity(user);
        return toDomainModel(jpaUserRepository.save(entity));
    }

    private User toDomainModel(UserEntity entity){
        return new User(
                entity.getId(),
                entity.getUsername(),
                entity.getEmail(),
                entity.getPassword(),
                entity.getCreatedAt(),
                entity.getAuthProvider()
        );

    }

    private UserEntity toEntity(User domain) {
        return UserEntity.builder()
                .id(domain.getId())
                .username(domain.getUsername())
                .email(domain.getEmail())
                .password(domain.getPassword())
                .createdAt(domain.getCreatedAt())
                .authProvider(domain.getAuthProvider())
                .build();
    }
}

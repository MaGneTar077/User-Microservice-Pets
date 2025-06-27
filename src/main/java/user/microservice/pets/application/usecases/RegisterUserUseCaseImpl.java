package user.microservice.pets.application.usecases;

import user.microservice.pets.domain.model.User;
import user.microservice.pets.domain.ports.in.RegisterUserUseCase;
import user.microservice.pets.domain.ports.out.UserRepositoryPort;

import java.time.LocalDateTime;
import java.util.UUID;

public class RegisterUserUseCaseImpl implements RegisterUserUseCase {
    private final UserRepositoryPort userRepositoryPort;

    public RegisterUserUseCaseImpl(UserRepositoryPort userRepositoryPort){
        this.userRepositoryPort = userRepositoryPort;
    }

    @Override
    public User register(User user){
        if (userRepositoryPort.existsByEmail(user.getEmail())){
            throw new IllegalArgumentException("Email already exists");
        }

        user.setId(UUID.randomUUID());
        user.setCreatedAt(LocalDateTime.now());

        return userRepositoryPort.save(user);
    }

}

package user.microservice.pets.application.usecases;

import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import user.microservice.pets.domain.model.User;
import user.microservice.pets.domain.ports.in.RegisterUserUseCase;
import user.microservice.pets.domain.ports.out.UserRepositoryPort;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class RegisterUserUseCaseImpl implements RegisterUserUseCase {

    private final UserRepositoryPort userRepositoryPort;
    private final PasswordEncoder passwordEncoder;

    @Override
    public User register(User user){
        if (userRepositoryPort.existsByEmail(user.getEmail())){
            throw new IllegalArgumentException("Email already exists");
        }

        user.setPassword(passwordEncoder.encode(user.getPassword()));

        user.setId(UUID.randomUUID());
        user.setCreatedAt(LocalDateTime.now());

        return userRepositoryPort.save(user);
    }
}

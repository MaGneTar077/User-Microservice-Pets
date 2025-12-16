package user.microservice.pets.application.usecases;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import user.microservice.pets.domain.enums.AuthProvider;
import user.microservice.pets.domain.model.User;
import user.microservice.pets.domain.ports.in.GoogleAuthUseCase;
import user.microservice.pets.domain.ports.out.UserRepositoryPort;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class GoogleAuthUseCaseImpl implements GoogleAuthUseCase {

    private final UserRepositoryPort userRepositoryPort;

    @Override
    public User authenticate(String email) {
        Optional<User> existingUser = userRepositoryPort.findByEmail(email);

        return existingUser.orElseGet(() -> {
            User newUser = new User();
            newUser.setId(UUID.randomUUID());
            newUser.setUsername(email.split("@")[0]);
            newUser.setEmail(email);
            newUser.setCreatedAt(LocalDateTime.now());
            newUser.setAuthProvider(AuthProvider.GOOGLE);
            return userRepositoryPort.save(newUser);
        });
    }
}
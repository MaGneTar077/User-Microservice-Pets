package user.microservice.pets.application.usecases;

import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import user.microservice.pets.domain.exceptions.InvalidUserDataException;
import user.microservice.pets.domain.exceptions.UserAlreadyExistsException;
import user.microservice.pets.domain.model.User;
import user.microservice.pets.domain.ports.in.RegisterUserUseCase;
import user.microservice.pets.domain.ports.out.UserRepositoryPort;

import java.time.LocalDateTime;
import java.util.UUID;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class RegisterUserUseCaseImpl implements RegisterUserUseCase {

    private final UserRepositoryPort userRepositoryPort;
    private final PasswordEncoder passwordEncoder;

    private static final Pattern EMAIL_PATTERN =
            Pattern.compile("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$");

    @Override
    public User register(User user) {
        validateUserData(user);
        checkDuplicates(user);

        user.setPassword(passwordEncoder.encode(user.getPassword()));
        user.setId(UUID.randomUUID());
        user.setCreatedAt(LocalDateTime.now());

        return userRepositoryPort.save(user);
    }

    private void validateUserData(User user) {

        if (user.getUsername() == null || user.getUsername().trim().isEmpty()) {
            throw new InvalidUserDataException("Username cannot be empty");
        }

        if (user.getEmail() == null || user.getEmail().trim().isEmpty()) {
            throw new InvalidUserDataException("Email cannot be empty");
        }

        if (user.getPassword() == null || user.getPassword().trim().isEmpty()) {
            throw new InvalidUserDataException("Password cannot be empty");
        }

        if (user.getUsername().length() < 3 || user.getUsername().length() > 50) {
            throw new InvalidUserDataException("Username must be between 3 and 50 characters");
        }

        if (!EMAIL_PATTERN.matcher(user.getEmail()).matches()) {
            throw new InvalidUserDataException("Invalid email format");
        }

        if (user.getPassword().length() < 6) {
            throw new InvalidUserDataException("Password must be at least 6 characters long");
        }

        if (!user.getUsername().matches("^[a-zA-Z0-9_]+$")) {
            throw new InvalidUserDataException("Username can only contain letters, numbers, and underscores");
        }
    }

    private void checkDuplicates(User user) {
        if (userRepositoryPort.existsByEmail(user.getEmail())) {
            throw new UserAlreadyExistsException("Email already exists");
        }

        if (userRepositoryPort.existsByUsername(user.getUsername())) {
            throw new UserAlreadyExistsException("Username already exists");
        }
    }
}
package user.microservice.pets.application.usecases;

import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import user.microservice.pets.application.services.EmailVerificationService;
import user.microservice.pets.domain.enums.AuthProvider;
import user.microservice.pets.domain.exceptions.InvalidUserDataException;
import user.microservice.pets.domain.exceptions.UserAlreadyExistsException;
import user.microservice.pets.domain.model.User;
import user.microservice.pets.domain.policies.PasswordPolicy;
import user.microservice.pets.domain.ports.in.RegisterUserUseCase;
import user.microservice.pets.domain.ports.out.UserRepositoryPort;

import java.time.LocalDateTime;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class RegisterUserUseCaseImpl implements RegisterUserUseCase {

    private static final Pattern EMAIL_PATTERN =
            Pattern.compile("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$");

    private final UserRepositoryPort userRepositoryPort;
    private final PasswordEncoder passwordEncoder;
    private final EmailVerificationService emailVerificationService;

    @Override
    public User register(User user) {
        normalize(user);
        validateUserData(user);
        checkDuplicates(user);

        user.setPassword(passwordEncoder.encode(user.getPassword()));
        user.setId(UUID.randomUUID());
        user.setCreatedAt(LocalDateTime.now());
        user.setAuthProvider(AuthProvider.LOCAL);
        user.setEmailVerified(false);

        User saved = userRepositoryPort.save(user);
        emailVerificationService.sendCode(saved);
        return saved;
    }

    private void normalize(User user) {
        if (user.getEmail() != null) {
            user.setEmail(user.getEmail().trim().toLowerCase(Locale.ROOT));
        }
        if (user.getUsername() != null) {
            user.setUsername(user.getUsername().trim());
        }
    }

    private void validateUserData(User user) {
        if (user.getUsername() == null || user.getUsername().isEmpty()) {
            throw new InvalidUserDataException("Username cannot be empty");
        }
        if (user.getUsername().length() < 3 || user.getUsername().length() > 50) {
            throw new InvalidUserDataException("Username must be between 3 and 50 characters");
        }
        if (!user.getUsername().matches("^[a-zA-Z0-9_]+$")) {
            throw new InvalidUserDataException("Username can only contain letters, numbers, and underscores");
        }
        if (user.getEmail() == null || user.getEmail().isEmpty()) {
            throw new InvalidUserDataException("Email cannot be empty");
        }
        if (user.getEmail().length() > 255 || !EMAIL_PATTERN.matcher(user.getEmail()).matches()) {
            throw new InvalidUserDataException("Invalid email format");
        }
        PasswordPolicy.validate(user.getPassword());
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
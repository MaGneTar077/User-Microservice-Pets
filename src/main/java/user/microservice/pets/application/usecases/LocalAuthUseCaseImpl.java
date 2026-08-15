package user.microservice.pets.application.usecases;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import user.microservice.pets.domain.enums.AuthProvider;
import user.microservice.pets.domain.exceptions.InvalidCredentialsException;
import user.microservice.pets.domain.exceptions.InvalidUserDataException;
import user.microservice.pets.domain.model.User;
import user.microservice.pets.domain.ports.in.LocalAuthUseCase;
import user.microservice.pets.domain.ports.out.UserRepositoryPort;

import java.util.regex.Pattern;

@Slf4j
@Service
@RequiredArgsConstructor
public class LocalAuthUseCaseImpl implements LocalAuthUseCase {

    private final UserRepositoryPort userRepositoryPort;
    private final PasswordEncoder passwordEncoder;

    private static final Pattern EMAIL_PATTERN =
            Pattern.compile("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$");

    @Override
    public User login(String email, String password) {

        validateLoginData(email, password);

        User user = userRepositoryPort.findByEmail(email.trim().toLowerCase())
                .filter(u -> u.getAuthProvider() == AuthProvider.LOCAL)
                .filter(u -> passwordEncoder.matches(password, u.getPassword()))
                .orElseThrow(() -> {
                    log.warn("Failed login attempt for email: {}", email);
                    return new InvalidCredentialsException("Invalid email or password");
                });

        log.info("Successful login for user: {}", user.getEmail());
        return user;
    }

    private void validateLoginData(String email, String password) {
        if (email == null || email.trim().isEmpty()) {
            throw new InvalidUserDataException("Email cannot be empty");
        }

        if (password == null || password.trim().isEmpty()) {
            throw new InvalidUserDataException("Password cannot be empty");
        }

        if (!EMAIL_PATTERN.matcher(email.trim()).matches()) {
            throw new InvalidUserDataException("Invalid email format");
        }

        if (password.length() < 8) {
            throw new InvalidUserDataException("Invalid credentials");
        }

        if (email.length() > 255) {
            throw new InvalidUserDataException("Email is too long");
        }

        if (password.length() > 128) {
            throw new InvalidUserDataException("Password is too long");
        }
    }
}
package user.microservice.pets.application.usecases;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import user.microservice.pets.domain.enums.AuthProvider;
import user.microservice.pets.domain.exceptions.EmailNotVerifiedException;
import user.microservice.pets.domain.exceptions.InvalidCredentialsException;
import user.microservice.pets.domain.exceptions.InvalidUserDataException;
import user.microservice.pets.domain.model.User;
import user.microservice.pets.domain.policies.PasswordPolicy;
import user.microservice.pets.domain.ports.in.LocalAuthUseCase;
import user.microservice.pets.domain.ports.out.UserRepositoryPort;

import java.util.Locale;
import java.util.regex.Pattern;

@Slf4j
@Service
@RequiredArgsConstructor
public class LocalAuthUseCaseImpl implements LocalAuthUseCase {

    private static final Pattern EMAIL_PATTERN =
            Pattern.compile("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$");

    private final UserRepositoryPort userRepositoryPort;
    private final PasswordEncoder passwordEncoder;

    @Override
    public User login(String email, String password) {
        if (email == null || email.isBlank()) {
            throw new InvalidUserDataException("Email cannot be empty");
        }
        if (password == null || password.isBlank()) {
            throw new InvalidUserDataException("Password cannot be empty");
        }
        if (email.length() > 255 || !EMAIL_PATTERN.matcher(email.trim()).matches()) {
            throw new InvalidUserDataException("Invalid email format");
        }
        if (!PasswordPolicy.hasValidLength(password)) {
            throw new InvalidCredentialsException("Invalid email or password");
        }

        String normalizedEmail = email.trim().toLowerCase(Locale.ROOT);

        User user = userRepositoryPort.findByEmail(normalizedEmail)
                .filter(u -> u.getAuthProvider() == AuthProvider.LOCAL)
                .filter(u -> u.getPassword() != null)
                .filter(u -> passwordEncoder.matches(password, u.getPassword()))
                .orElseThrow(() -> {
                    log.warn("Failed login attempt for email: {}", normalizedEmail);
                    return new InvalidCredentialsException("Invalid email or password");
                });

        if (!user.isEmailVerified()) {
            throw new EmailNotVerifiedException();
        }

        log.info("Successful login for user: {}", user.getEmail());
        return user;
    }
}
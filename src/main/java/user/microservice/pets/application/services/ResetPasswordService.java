package user.microservice.pets.application.services;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import user.microservice.pets.domain.exceptions.ExpiredPasswordResetTokenException;
import user.microservice.pets.domain.exceptions.InvalidPasswordResetTokenException;
import user.microservice.pets.domain.exceptions.InvalidUserDataException;
import user.microservice.pets.domain.exceptions.UserNotFoundException;
import user.microservice.pets.domain.model.PasswordResetToken;
import user.microservice.pets.domain.model.User;
import user.microservice.pets.domain.ports.in.ResetPasswordUseCase;
import user.microservice.pets.domain.ports.out.EmailSenderPort;
import user.microservice.pets.domain.ports.out.PasswordResetTokenRepositoryPort;
import user.microservice.pets.domain.ports.out.UserRepositoryPort;

import java.time.LocalDateTime;
import java.util.regex.Pattern;

@Slf4j
@Service
@RequiredArgsConstructor
public class ResetPasswordService implements ResetPasswordUseCase {

    private final UserRepositoryPort userRepository;
    private final PasswordResetTokenRepositoryPort tokenRepository;
    private final EmailSenderPort emailService;
    private final PasswordEncoder passwordEncoder;

    private static final Pattern PASSWORD_PATTERN =
            Pattern.compile("^(?=.*[0-9])(?=.*[a-z])(?=.*[A-Z])(?=.*[@#$%^&+=!])(?=\\S+$).{8,}$");

    @Override
    @Transactional
    public void execute(String token, String newPassword) {

        validateInputs(token, newPassword);

        PasswordResetToken resetToken = tokenRepository.findByToken(token.trim())
                .orElseThrow(() -> {
                    log.warn("Invalid password reset token attempted: {}", token);
                    return new InvalidPasswordResetTokenException("Invalid or expired token");
                });

        if (resetToken.isUsed()) {
            log.warn("Attempt to reuse password reset token for email: {}", resetToken.getEmail());
            tokenRepository.deleteByToken(token.trim());
            throw new InvalidPasswordResetTokenException("Token has already been used");
        }

        if (resetToken.getExpiresAt().isBefore(LocalDateTime.now())) {
            log.warn("Expired password reset token for email: {}", resetToken.getEmail());
            tokenRepository.deleteByToken(token.trim());
            throw new ExpiredPasswordResetTokenException("Token has expired");
        }

        User user = userRepository.findByEmail(resetToken.getEmail())
                .orElseThrow(() -> {
                    log.error("User not found for password reset token email: {}", resetToken.getEmail());
                    tokenRepository.deleteByToken(token.trim());
                    return new UserNotFoundException("User not found");
                });

        if (passwordEncoder.matches(newPassword, user.getPassword())) {
            throw new InvalidUserDataException("New password must be different from current password");
        }

        user.setPassword(passwordEncoder.encode(newPassword));
        userRepository.save(user);

        tokenRepository.deleteByToken(token.trim());

        try {
            emailService.sendEmail(
                    user.getEmail(),
                    "Contraseña cambiada con éxito",
                    buildSuccessEmailBody(user.getUsername())
            );
            log.info("Password successfully reset for user: {}", user.getEmail());
        } catch (Exception e) {
            log.error("Failed to send password reset confirmation email to: {}", user.getEmail(), e);
            // No fallar la operación si el email falla
        }
    }

    private void validateInputs(String token, String newPassword) {
        // Validar token
        if (token == null || token.trim().isEmpty()) {
            throw new InvalidUserDataException("Token cannot be empty");
        }

        if (token.length() > 255) {
            throw new InvalidUserDataException("Invalid token format");
        }

        // Validar nueva contraseña
        if (newPassword == null || newPassword.trim().isEmpty()) {
            throw new InvalidUserDataException("Password cannot be empty");
        }

        if (newPassword.length() < 8) {
            throw new InvalidUserDataException("Password must be at least 8 characters long");
        }

        if (newPassword.length() > 128) {
            throw new InvalidUserDataException("Password is too long");
        }

        if (!PASSWORD_PATTERN.matcher(newPassword).matches()) {
            throw new InvalidUserDataException(
                    "Password must contain at least one uppercase letter, one lowercase letter, " +
                            "one digit, and one special character"
            );
        }
    }

    private String buildSuccessEmailBody(String username) {
        return """
            Hola %s,
            
            Tu contraseña ha sido cambiada exitosamente.
            
            Si no realizaste este cambio, por favor contacta a soporte inmediatamente.
            
            Saludos,
            El equipo de soporte
            """.formatted(username != null ? username : "");
    }
}
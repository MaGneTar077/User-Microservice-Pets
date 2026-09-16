package user.microservice.pets.application.services;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import user.microservice.pets.domain.enums.AuthProvider;
import user.microservice.pets.domain.exceptions.ExpiredPasswordResetTokenException;
import user.microservice.pets.domain.exceptions.InvalidPasswordResetTokenException;
import user.microservice.pets.domain.exceptions.InvalidUserDataException;
import user.microservice.pets.domain.model.PasswordResetToken;
import user.microservice.pets.domain.model.User;
import user.microservice.pets.domain.policies.PasswordPolicy;
import user.microservice.pets.domain.ports.in.ResetPasswordUseCase;
import user.microservice.pets.domain.ports.out.EmailSenderPort;
import user.microservice.pets.domain.ports.out.PasswordResetTokenRepositoryPort;
import user.microservice.pets.domain.ports.out.UserRepositoryPort;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class ResetPasswordService implements ResetPasswordUseCase {

    private final UserRepositoryPort userRepository;
    private final PasswordResetTokenRepositoryPort tokenRepository;
    private final EmailSenderPort emailService;
    private final PasswordEncoder passwordEncoder;

    @Override
    @Transactional(noRollbackFor = {
            InvalidPasswordResetTokenException.class,
            ExpiredPasswordResetTokenException.class
    })
    public void execute(String rawToken, String newPassword) {
        validateInputs(rawToken, newPassword);
        String token = rawToken.trim();

        PasswordResetToken resetToken = tokenRepository.findByToken(token)
                .orElseThrow(() -> {
                    log.warn("Invalid password reset token attempted");
                    return new InvalidPasswordResetTokenException("Invalid or expired token");
                });

        if (resetToken.isUsed()) {
            tokenRepository.deleteByToken(token);
            throw new InvalidPasswordResetTokenException("Token has already been used");
        }

        if (resetToken.getExpiresAt().isBefore(LocalDateTime.now())) {
            tokenRepository.deleteByToken(token);
            throw new ExpiredPasswordResetTokenException("Token has expired");
        }

        User user = userRepository.findByEmail(resetToken.getEmail()).orElse(null);
        if (user == null || user.getAuthProvider() != AuthProvider.LOCAL) {
            log.warn("Password reset token for missing or non-local account: {}", resetToken.getEmail());
            tokenRepository.deleteByToken(token);
            throw new InvalidPasswordResetTokenException("Invalid or expired token");
        }

        // Esta excepción SÍ hace rollback: el token se conserva para que el usuario lo intente de nuevo
        if (user.getPassword() != null && passwordEncoder.matches(newPassword, user.getPassword())) {
            throw new InvalidUserDataException("New password must be different from current password");
        }

        user.setPassword(passwordEncoder.encode(newPassword));
        // Usar el enlace del correo demuestra que el email es suyo
        user.setEmailVerified(true);
        userRepository.save(user);

        tokenRepository.deleteByEmail(user.getEmail());

        try {
            emailService.sendEmail(
                    user.getEmail(),
                    "Tu contraseña de MyAnimaLog fue cambiada",
                    buildSuccessEmailBody(user.getUsername()));
        } catch (Exception e) {
            log.error("Failed to send password change confirmation to {}: {}", user.getEmail(), e.getMessage());
        }

        log.info("Password successfully reset for user: {}", user.getEmail());
    }

    private void validateInputs(String token, String newPassword) {
        if (token == null || token.isBlank()) {
            throw new InvalidUserDataException("Token cannot be empty");
        }
        if (token.length() > 255) {
            throw new InvalidUserDataException("Invalid token format");
        }
        PasswordPolicy.validate(newPassword);
    }

    private String buildSuccessEmailBody(String username) {
        return """
            Hola %s,

            La contraseña de tu cuenta de MyAnimaLog fue cambiada correctamente.

            Si no realizaste este cambio, restablece tu contraseña de inmediato
            y contáctanos respondiendo a este correo.

            Saludos,
            El equipo de MyAnimaLog
            """.formatted(username != null ? username : "");
    }
}
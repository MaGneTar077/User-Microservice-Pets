package user.microservice.pets.application.services;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import user.microservice.pets.domain.exceptions.InvalidUserDataException;
import user.microservice.pets.domain.model.PasswordResetToken;
import user.microservice.pets.domain.model.User;
import user.microservice.pets.domain.ports.in.RequestPasswordResetUseCase;
import user.microservice.pets.domain.ports.out.EmailSenderPort;
import user.microservice.pets.domain.ports.out.PasswordResetTokenRepositoryPort;
import user.microservice.pets.domain.ports.out.UserRepositoryPort;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

@Slf4j
@Service
@Transactional
@RequiredArgsConstructor
public class RequestPasswordResetService implements RequestPasswordResetUseCase {

    private final UserRepositoryPort userRepository;
    private final PasswordResetTokenRepositoryPort tokenRepository;
    private final EmailSenderPort emailService;

    private static final Pattern EMAIL_PATTERN =
            Pattern.compile("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$");

    @Override
    public void execute(String email) {

        validateEmail(email);

        Optional<User> userOpt = userRepository.findByEmail(email.trim().toLowerCase());

        if (userOpt.isEmpty()) {
            log.warn("Password reset requested for non-existent email: {}", email);
            return;
        }

        tokenRepository.deleteByEmail(email.trim().toLowerCase());

        String token = UUID.randomUUID().toString();
        LocalDateTime expiresAt = LocalDateTime.now().plusMinutes(15);
        PasswordResetToken resetToken = new PasswordResetToken(
                token,
                email.trim().toLowerCase(),
                expiresAt
        );

        tokenRepository.save(resetToken);

        try {
            String resetLink = "https://tudominio.com/reset-password?token=" + token;
            emailService.sendEmail(
                    email.trim().toLowerCase(),
                    "Recuperación de contraseña",
                    buildEmailBody(resetLink)
            );
            log.info("Password reset email sent to: {}", email);
        } catch (Exception e) {
            log.error("Failed to send password reset email to: {}", email, e);
        }
    }

    private void validateEmail(String email) {
        if (email == null || email.trim().isEmpty()) {
            throw new InvalidUserDataException("Email cannot be empty");
        }

        if (!EMAIL_PATTERN.matcher(email.trim()).matches()) {
            throw new InvalidUserDataException("Invalid email format");
        }

        if (email.length() > 255) {
            throw new InvalidUserDataException("Email is too long");
        }
    }

    private String buildEmailBody(String resetLink) {
        return """
            Hola,
            
            Recibimos una solicitud para restablecer tu contraseña.
            
            Haz clic en el siguiente enlace para crear una nueva contraseña:
            %s
            
            Este enlace expirará en 15 minutos.
            
            Si no solicitaste restablecer tu contraseña, ignora este correo.
            
            Saludos,
            El equipo de soporte
            """.formatted(resetLink);
    }
}
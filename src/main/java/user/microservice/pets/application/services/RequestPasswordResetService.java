package user.microservice.pets.application.services;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import user.microservice.pets.domain.enums.AuthProvider;
import user.microservice.pets.domain.exceptions.InvalidUserDataException;
import user.microservice.pets.domain.model.PasswordResetToken;
import user.microservice.pets.domain.model.User;
import user.microservice.pets.domain.ports.in.RequestPasswordResetUseCase;
import user.microservice.pets.domain.ports.out.EmailSenderPort;
import user.microservice.pets.domain.ports.out.PasswordResetTokenRepositoryPort;
import user.microservice.pets.domain.ports.out.UserRepositoryPort;

import java.time.LocalDateTime;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

@Slf4j
@Service
public class RequestPasswordResetService implements RequestPasswordResetUseCase {

    private static final long TOKEN_VALIDITY_MINUTES = 15;
    private static final Pattern EMAIL_PATTERN =
            Pattern.compile("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$");

    private final UserRepositoryPort userRepository;
    private final PasswordResetTokenRepositoryPort tokenRepository;
    private final EmailSenderPort emailService;
    private final String frontendUrl;

    public RequestPasswordResetService(UserRepositoryPort userRepository,
                                       PasswordResetTokenRepositoryPort tokenRepository,
                                       EmailSenderPort emailService,
                                       @Value("${app.frontend-url}") String frontendUrl) {
        this.userRepository = userRepository;
        this.tokenRepository = tokenRepository;
        this.emailService = emailService;
        this.frontendUrl = frontendUrl.endsWith("/")
                ? frontendUrl.substring(0, frontendUrl.length() - 1)
                : frontendUrl;
    }

    @Override
    @Transactional
    public void execute(String rawEmail) {
        validateEmail(rawEmail);
        String email = rawEmail.trim().toLowerCase(Locale.ROOT);

        Optional<User> userOpt = userRepository.findByEmail(email);
        if (userOpt.isEmpty()) {
            log.info("Password reset requested for non-existent email");
            return; // misma respuesta para no revelar si existe
        }

        User user = userOpt.get();

        // Las cuentas de Google no tienen contraseña local
        if (user.getAuthProvider() != AuthProvider.LOCAL) {
            sendSafely(email, "Tu cuenta de MyAnimaLog usa Google", buildGoogleAccountBody(user.getUsername()));
            return;
        }

        tokenRepository.deleteByEmail(email);

        String token = UUID.randomUUID().toString();
        tokenRepository.save(new PasswordResetToken(
                token,
                email,
                LocalDateTime.now().plusMinutes(TOKEN_VALIDITY_MINUTES)));

        String resetLink = frontendUrl + "/reset-password?token=" + token;
        sendSafely(email, "Recupera tu contraseña de MyAnimaLog", buildResetBody(user.getUsername(), resetLink));
    }

    private void sendSafely(String to, String subject, String body) {
        try {
            emailService.sendEmail(to, subject, body);
            log.info("Password reset email sent to: {}", to);
        } catch (Exception e) {
            log.error("Failed to send password reset email to {}: {}", to, e.getMessage());
        }
    }

    private void validateEmail(String email) {
        if (email == null || email.isBlank()) {
            throw new InvalidUserDataException("Email cannot be empty");
        }
        if (email.length() > 255) {
            throw new InvalidUserDataException("Email is too long");
        }
        if (!EMAIL_PATTERN.matcher(email.trim()).matches()) {
            throw new InvalidUserDataException("Invalid email format");
        }
    }

    private String buildResetBody(String username, String resetLink) {
        return """
            Hola %s,

            Recibimos una solicitud para restablecer tu contraseña de MyAnimaLog.

            Haz clic en el siguiente enlace para crear una nueva contraseña:
            %s

            Este enlace expira en %d minutos.

            Si no solicitaste este cambio, ignora este correo; tu contraseña seguirá igual.

            Saludos,
            El equipo de MyAnimaLog
            """.formatted(username != null ? username : "", resetLink, TOKEN_VALIDITY_MINUTES);
    }

    private String buildGoogleAccountBody(String username) {
        return """
            Hola %s,

            Recibimos una solicitud para restablecer tu contraseña, pero tu cuenta
            de MyAnimaLog está vinculada a Google y no usa contraseña propia.

            Para entrar, usa el botón "Continuar con Google" en la aplicación.

            Si no hiciste esta solicitud, ignora este correo.

            Saludos,
            El equipo de MyAnimaLog
            """.formatted(username != null ? username : "");
    }
}
package user.microservice.pets.application.services;

import org.springframework.stereotype.Service;
import user.microservice.pets.domain.model.PasswordResetToken;
import user.microservice.pets.domain.model.User;
import user.microservice.pets.domain.ports.in.RequestPasswordResetUseCase;
import user.microservice.pets.domain.ports.out.EmailSenderPort;
import user.microservice.pets.domain.ports.out.PasswordResetTokenRepositoryPort;
import user.microservice.pets.domain.ports.out.UserRepositoryPort;


import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

@Service
public class RequestPasswordResetService implements RequestPasswordResetUseCase{

    private final UserRepositoryPort userRepository;
    private final PasswordResetTokenRepositoryPort tokenRepository;
    private final EmailSenderPort emailService;

    public RequestPasswordResetService(UserRepositoryPort userRepository,
                                       PasswordResetTokenRepositoryPort tokenRepository,
                                       EmailSenderPort emailService) {
        this.userRepository = userRepository;
        this.tokenRepository = tokenRepository;
        this.emailService = emailService;
    }

    @Override
    public void execute(String email) {
        Optional<User> userOpt = userRepository.findByEmail(email);
        if (userOpt.isEmpty()) {
            return; // No filtramos si el usuario no existe por seguridad
        }

        String token = UUID.randomUUID().toString();
        LocalDateTime expiresAt = LocalDateTime.now().plusMinutes(15);
        PasswordResetToken resetToken = new PasswordResetToken(token, email, expiresAt);

        tokenRepository.save(resetToken);

        String resetLink = "https://tudominio.com/reset-password?token=" + token;
        emailService.sendEmail(email, "Recuperación de contraseña",
                "Haz clic aquí para restablecer tu contraseña: " + resetLink);
    }
}


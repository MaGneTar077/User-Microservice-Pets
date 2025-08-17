package user.microservice.pets.application.services;

import org.springframework.stereotype.Service;
import user.microservice.pets.domain.model.PasswordResetToken;
import user.microservice.pets.domain.model.User;
import user.microservice.pets.domain.ports.in.ResetPasswordUseCase;
import user.microservice.pets.domain.ports.out.EmailSenderPort;
import user.microservice.pets.domain.ports.out.PasswordResetTokenRepositoryPort;
import user.microservice.pets.domain.ports.out.UserRepositoryPort;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDateTime;
import java.util.Optional;

@Service
public class ResetPasswordService implements ResetPasswordUseCase {

    private final UserRepositoryPort userRepository;
    private final PasswordResetTokenRepositoryPort tokenRepository;
    private final EmailSenderPort emailService;
    private final PasswordEncoder passwordEncoder;

    public ResetPasswordService(UserRepositoryPort userRepository,
                                PasswordResetTokenRepositoryPort tokenRepository,
                                EmailSenderPort emailService,
                                PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.tokenRepository = tokenRepository;
        this.emailService = emailService;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void execute(String token, String newPassword) {
        Optional<PasswordResetToken> tokenOpt = tokenRepository.findByToken(token);
        if (tokenOpt.isEmpty()) {
            throw new IllegalArgumentException("Token inválido");
        }

        PasswordResetToken resetToken = tokenOpt.get();
        if (resetToken.getExpiresAt().isBefore(LocalDateTime.now())) {
            tokenRepository.deleteByToken(token);
            throw new IllegalArgumentException("Token expirado");
        }

        User user = userRepository.findByEmail(resetToken.getEmail())
                .orElseThrow(() -> new IllegalArgumentException("Usuario no encontrado"));

        user.setPassword(passwordEncoder.encode(newPassword));
        userRepository.save(user);

        tokenRepository.deleteByToken(token);

        emailService.sendEmail(user.getEmail(), "Contraseña cambiada con éxito",
                "Tu contraseña fue actualizada correctamente.");
    }
}

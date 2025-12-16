package user.microservice.pets.application.services;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
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
@RequiredArgsConstructor
public class ResetPasswordService implements ResetPasswordUseCase {

    private final UserRepositoryPort userRepository;
    private final PasswordResetTokenRepositoryPort tokenRepository;
    private final EmailSenderPort emailService;
    private final PasswordEncoder passwordEncoder;

    @Override
    @Transactional
    public void execute(String token, String newPassword) {
        PasswordResetToken resetToken = tokenRepository.findByToken(token)
                .orElseThrow(() -> new IllegalArgumentException("Token inválido o ya usado"));

        if (resetToken.getExpiresAt().isBefore(LocalDateTime.now())) {
            tokenRepository.deleteByToken(token);
            throw new IllegalArgumentException("Token expirado");
        }

        if (resetToken.isUsed()) {
            throw new IllegalArgumentException("Token ya utilizado");
        }

        User user = userRepository.findByEmail(resetToken.getEmail())
                .orElseThrow(() -> new IllegalArgumentException("Usuario no encontrado"));

        user.setPassword(passwordEncoder.encode(newPassword));
        userRepository.save(user);

        tokenRepository.deleteByToken(token);

        emailService.sendEmail(user.getEmail(),
                "Contraseña cambiada con éxito",
                "Tu contraseña fue actualizada correctamente.");
    }
}

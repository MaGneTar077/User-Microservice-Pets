package user.microservice.pets.infrastructure.jobs;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import user.microservice.pets.domain.ports.out.PasswordResetTokenRepositoryPort;

@Slf4j
@Component
@RequiredArgsConstructor
public class TokenCleanupJob {

    private final PasswordResetTokenRepositoryPort tokenRepository;

    // Ejecutar cada hora
    @Scheduled(cron = "0 0 * * * *")
    public void cleanupExpiredTokens() {
        log.info("Iniciando limpieza de tokens expirados...");
        try {
            tokenRepository.deleteAllExpiredTokens();
            log.info("Limpieza de tokens completada exitosamente");
        } catch (Exception e) {
            log.error("Error al limpiar tokens expirados: {}", e.getMessage());
        }
    }
}

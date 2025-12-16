package user.microservice.pets.domain.ports.out;

import user.microservice.pets.domain.model.PasswordResetToken;
import java.util.Optional;

public interface PasswordResetTokenRepositoryPort {
    PasswordResetToken save(PasswordResetToken token);
    Optional<PasswordResetToken> findByToken(String token);
    void deleteByToken(String token);
    void deleteByEmail(String email);
    void deleteAllExpiredTokens();
}

package user.microservice.pets.application.usecases;

import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import user.microservice.pets.application.services.GoogleTokenVerifierService;
import user.microservice.pets.domain.enums.AuthProvider;
import user.microservice.pets.domain.model.User;
import user.microservice.pets.domain.ports.in.GoogleAuthUseCase;
import user.microservice.pets.domain.ports.out.UserRepositoryPort;

import java.time.LocalDateTime;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class GoogleAuthUseCaseImpl implements GoogleAuthUseCase {

    private final UserRepositoryPort userRepositoryPort;
    private final GoogleTokenVerifierService googleTokenVerifierService;

    @Override
    public User authenticate(String idToken) {
        GoogleIdToken.Payload payload = googleTokenVerifierService.verify(idToken);

        String email = payload.getEmail().trim().toLowerCase(Locale.ROOT);
        String name = (String) payload.get("name");
        String picture = (String) payload.get("picture");

        Optional<User> existing = userRepositoryPort.findByEmail(email);

        if (existing.isPresent()) {
            User user = existing.get();

            if (!user.isEmailVerified()) {
                log.warn("Unverified local account for {} claimed via Google. Removing local password.", email);
                user.setPassword(null);
                user.setAuthProvider(AuthProvider.GOOGLE);
                user.setEmailVerified(true);
                if (user.getProfileImageUrl() == null) {
                    user.setProfileImageUrl(picture);
                }
                return userRepositoryPort.save(user);
            }
            return user;
        }

        String base = (name != null && !name.isBlank())
                ? name.replaceAll("[^A-Za-z0-9_]", "").toLowerCase(Locale.ROOT)
                : "";
        if (base.length() < 3) {
            base = email.split("@")[0].replaceAll("[^A-Za-z0-9_]", "").toLowerCase(Locale.ROOT);
        }

        String username = base;
        int suffix = 1;
        while (userRepositoryPort.existsByUsername(username)) {
            username = base + suffix++;
        }

        return userRepositoryPort.save(User.builder()
                .id(UUID.randomUUID())
                .username(username)
                .email(email)
                .profileImageUrl(picture)
                .createdAt(LocalDateTime.now())
                .authProvider(AuthProvider.GOOGLE)
                .emailVerified(true)
                .build());
    }
}
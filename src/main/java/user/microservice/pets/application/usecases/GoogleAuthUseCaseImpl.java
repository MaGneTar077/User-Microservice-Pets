package user.microservice.pets.application.usecases;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import user.microservice.pets.application.services.GoogleTokenVerifierService;
import user.microservice.pets.domain.enums.AuthProvider;
import user.microservice.pets.domain.model.User;
import user.microservice.pets.domain.ports.in.GoogleAuthUseCase;
import user.microservice.pets.domain.ports.out.UserRepositoryPort;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class GoogleAuthUseCaseImpl implements GoogleAuthUseCase {

    private final UserRepositoryPort userRepositoryPort;
    private final GoogleTokenVerifierService googleTokenVerifierService;

    @Override
    public User authenticate(String idToken) {
        com.google.api.client.googleapis.auth.oauth2.GoogleIdToken.Payload payload =
                googleTokenVerifierService.verify(idToken);

        String email = payload.getEmail();
        String name = (String) payload.get("name");
        String picture = (String) payload.get("picture");

        Optional<User> existingUser = userRepositoryPort.findByEmail(email);

        return existingUser.orElseGet(() -> {
            String username = (name != null && !name.isBlank())
                    ? name.replaceAll("\\s+", "").toLowerCase()
                    : email.split("@")[0];

            String finalUsername = username;
            int suffix = 1;
            while (userRepositoryPort.existsByUsername(finalUsername)) {
                finalUsername = username + suffix++;
            }

            User newUser = User.builder()
                    .id(UUID.randomUUID())
                    .username(finalUsername)
                    .email(email)
                    .profileImageUrl(picture)
                    .createdAt(LocalDateTime.now())
                    .authProvider(AuthProvider.GOOGLE)
                    .build();

            return userRepositoryPort.save(newUser);
        });
    }
}
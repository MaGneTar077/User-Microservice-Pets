package user.microservice.pets.infrastructure.controllers;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import user.microservice.pets.application.dto.ProfileResponse;
import user.microservice.pets.domain.exceptions.UnauthorizedAccessException;
import user.microservice.pets.domain.exceptions.InvalidUserDataException;
import user.microservice.pets.domain.model.User;
import user.microservice.pets.domain.ports.in.GetProfileUseCase;
import user.microservice.pets.domain.ports.out.UserRepositoryPort;

import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/user")
@RequiredArgsConstructor
public class GetProfileController {

    private final GetProfileUseCase getProfileUseCase;
    private final UserRepositoryPort userRepositoryPort;

    @GetMapping("/profile/{id}")
    public ResponseEntity<ProfileResponse> getProfile(@PathVariable String id) {
        UUID userId;
        try {
            userId = UUID.fromString(id);
        } catch (IllegalArgumentException e) {
            log.warn("Invalid UUID format: {}", id);
            throw new InvalidUserDataException("Invalid user ID format");
        }


        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            log.warn("Unauthenticated attempt to access profile");
            throw new UnauthorizedAccessException("Authentication required");
        }

        String authenticatedEmail = authentication.getName();

        User authenticatedUser = userRepositoryPort.findByEmail(authenticatedEmail)
                .orElseThrow(() -> new UnauthorizedAccessException("User not found"));

        if (!authenticatedUser.getId().equals(userId)) {
            log.warn("User {} attempted to access profile of user {}",
                    authenticatedEmail, userId);
            throw new UnauthorizedAccessException("You can only access your own profile");
        }

        User user = getProfileUseCase.getProfile(userId);

        ProfileResponse response = new ProfileResponse(
                user.getId(),
                user.getUsername(),
                user.getEmail(),
                user.getProfileImageUrl(),
                user.getCreatedAt(),
                user.getAuthProvider()
        );

        return ResponseEntity.ok(response);
    }
}
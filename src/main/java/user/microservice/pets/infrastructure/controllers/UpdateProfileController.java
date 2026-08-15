package user.microservice.pets.infrastructure.controllers;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import user.microservice.pets.application.dto.UpdateUserRequest;
import user.microservice.pets.application.dto.UpdateUserResponse;
import user.microservice.pets.domain.exceptions.InvalidUserDataException;
import user.microservice.pets.domain.exceptions.UnauthorizedAccessException;
import user.microservice.pets.domain.model.User;
import user.microservice.pets.domain.ports.in.UpdateUserProfileUseCase;
import user.microservice.pets.domain.ports.out.UserRepositoryPort;

import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/user")
@RequiredArgsConstructor
public class UpdateProfileController {

    private final UpdateUserProfileUseCase updateUserProfileUseCase;
    private final UserRepositoryPort userRepositoryPort;

    @PutMapping("/{id}")
    public ResponseEntity<UpdateUserResponse> updateProfile(
            @PathVariable String id,
            @Valid @RequestBody UpdateUserRequest request) {

        UUID userId;
        try {
            userId = UUID.fromString(id);
        } catch (IllegalArgumentException e) {
            log.warn("Invalid UUID format for update: {}", id);
            throw new InvalidUserDataException("Invalid user ID format");
        }

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            log.warn("Unauthenticated attempt to update profile");
            throw new UnauthorizedAccessException("Authentication required");
        }

        String authenticatedEmail = authentication.getName();

        User authenticatedUser = userRepositoryPort.findByEmail(authenticatedEmail)
                .orElseThrow(() -> new UnauthorizedAccessException("User not found"));

        if (!authenticatedUser.getId().equals(userId)) {
            log.warn("User {} attempted to update profile of user {}",
                    authenticatedEmail, userId);
            throw new UnauthorizedAccessException("You can only update your own profile");
        }

        User domain = User.builder()
                .id(userId)
                .username(request.getUsername())
                .build();

        User updated = updateUserProfileUseCase.updateProfile(domain);

        UpdateUserResponse response = new UpdateUserResponse(
                updated.getId(),
                updated.getUsername(),
                updated.getEmail()
        );

        log.info("Profile updated successfully for user: {}", updated.getEmail());
        return ResponseEntity.ok(response);
    }
}
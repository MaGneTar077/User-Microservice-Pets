package user.microservice.pets.infrastructure.controllers;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import user.microservice.pets.application.dto.UpdateUserRequest;
import user.microservice.pets.application.dto.UpdateUserResponse;
import user.microservice.pets.domain.model.User;
import user.microservice.pets.domain.ports.in.UpdateUserProfileUseCase;
import user.microservice.pets.domain.ports.out.UserRepositoryPort;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class UpdateProfileControllerTest {

    private UpdateUserProfileUseCase updateUserProfileUseCase;
    private UserRepositoryPort userRepositoryPort;
    private UpdateProfileController updateProfileController;

    @BeforeEach
    void setup() {
        updateUserProfileUseCase = mock(UpdateUserProfileUseCase.class);
        userRepositoryPort = mock(UserRepositoryPort.class);

        updateProfileController =
                new UpdateProfileController(updateUserProfileUseCase, userRepositoryPort);
    }

    @AfterEach
    void cleanup() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void shouldReturn200WhenProfileIsUpdated() {
        // Given
        UUID userId = UUID.randomUUID();
        String email = "user@example.com";

        UpdateUserRequest request = new UpdateUserRequest();
        request.setUsername("newUsername");

        User authenticatedUser = User.builder()
                .id(userId)
                .email(email)
                .build();

        User updatedUser = User.builder()
                .id(userId)
                .username("newUsername")
                .email(email)
                .build();

        Authentication authentication = mock(Authentication.class);
        when(authentication.isAuthenticated()).thenReturn(true);
        when(authentication.getName()).thenReturn(email);

        SecurityContext securityContext = mock(SecurityContext.class);
        when(securityContext.getAuthentication()).thenReturn(authentication);

        SecurityContextHolder.setContext(securityContext);

        when(userRepositoryPort.findByEmail(email))
                .thenReturn(Optional.of(authenticatedUser));

        when(updateUserProfileUseCase.updateProfile(any(User.class)))
                .thenReturn(updatedUser);

        // When
        ResponseEntity<UpdateUserResponse> response =
                updateProfileController.updateProfile(userId.toString(), request);

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getId()).isEqualTo(userId);
        assertThat(response.getBody().getUsername()).isEqualTo("newUsername");
        assertThat(response.getBody().getEmail()).isEqualTo(email);

        verify(updateUserProfileUseCase, times(1))
                .updateProfile(any(User.class));
    }
}

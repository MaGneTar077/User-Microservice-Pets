package user.microservice.pets.infrastructure.controllers;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import user.microservice.pets.application.dto.UpdateUserRequest;
import user.microservice.pets.application.dto.UpdateUserResponse;
import user.microservice.pets.domain.model.User;
import user.microservice.pets.domain.ports.in.UpdateUserProfileUseCase;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class UpdateProfileControllerTest {

    private UpdateUserProfileUseCase updateUserProfileUseCase;
    private UpdateProfileController updateProfileController;

    @BeforeEach
    void setup() {
        updateUserProfileUseCase = mock(UpdateUserProfileUseCase.class);
        updateProfileController = new UpdateProfileController(updateUserProfileUseCase);
    }

    @Test
    void shouldReturn200WhenProfileIsUpdated() {
        // Given
        UUID userId = UUID.randomUUID();

        UpdateUserRequest request = new UpdateUserRequest();
        request.setUsername("newUsername");

        User updatedUser = User.builder()
                .id(userId)
                .username("newUsername")
                .email("user@example.com")
                .build();

        when(updateUserProfileUseCase.updateProfile(any(User.class)))
                .thenReturn(updatedUser);

        // When
        ResponseEntity<UpdateUserResponse> response =
                updateProfileController.updateProfile(userId, request);

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getId()).isEqualTo(userId);
        assertThat(response.getBody().getUsername()).isEqualTo("newUsername");
        assertThat(response.getBody().getEmail()).isEqualTo("user@example.com");

        verify(updateUserProfileUseCase, times(1))
                .updateProfile(any(User.class));
    }
}

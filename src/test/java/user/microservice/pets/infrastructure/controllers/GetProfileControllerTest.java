package user.microservice.pets.infrastructure.controllers;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import user.microservice.pets.application.dto.ProfileResponse;
import user.microservice.pets.domain.enums.AuthProvider;
import user.microservice.pets.domain.model.User;
import user.microservice.pets.domain.ports.in.GetProfileUseCase;
import user.microservice.pets.domain.ports.out.UserRepositoryPort;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("GetProfileController - Unit Tests")
class GetProfileControllerTest {

    @Mock
    private GetProfileUseCase getProfileUseCase;

    @Mock
    private UserRepositoryPort userRepositoryPort;

    @InjectMocks
    private GetProfileController getProfileController;

    @Test
    @DisplayName("Should return profile when authenticated user requests own profile")
    void shouldReturnProfileSuccessfully() {

        // GIVEN
        UUID userId = UUID.randomUUID();
        String email = "test@example.com";

        User authenticatedUser = User.builder()
                .id(userId)
                .email(email)
                .username("testuser")
                .authProvider(AuthProvider.LOCAL)
                .createdAt(LocalDateTime.now())
                .build();

        // Simular usuario autenticado
        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(email, null, null);
        SecurityContextHolder.getContext().setAuthentication(authentication);

        when(userRepositoryPort.findByEmail(email))
                .thenReturn(Optional.of(authenticatedUser));

        when(getProfileUseCase.getProfile(userId))
                .thenReturn(authenticatedUser);

        // WHEN
        var responseEntity =
                getProfileController.getProfile(userId.toString());

        ProfileResponse response = responseEntity.getBody();

        // THEN
        assertThat(responseEntity.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response).isNotNull();
        assertThat(response.getId()).isEqualTo(userId);
        assertThat(response.getEmail()).isEqualTo(email);
        assertThat(response.getUsername()).isEqualTo("testuser");
        assertThat(response.getAuthProvider()).isEqualTo(AuthProvider.LOCAL);
    }
}

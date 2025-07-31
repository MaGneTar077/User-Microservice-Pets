package user.microservice.pets.infrastructure.controllers;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import user.microservice.pets.domain.enums.AuthProvider;
import user.microservice.pets.domain.model.User;
import user.microservice.pets.domain.ports.in.GoogleAuthUseCase;
import user.microservice.pets.domain.ports.in.LocalAuthUseCase;
import user.microservice.pets.infrastructure.security.JwtUtil;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

public class AuthControllerTest {

    private GoogleAuthUseCase googleAuthUseCase;
    private JwtUtil jwtUtil;
    private LocalAuthUseCase localAuthUseCase;
    private AuthController authController;

    @BeforeEach
    void setup() {
        googleAuthUseCase = mock(GoogleAuthUseCase.class);
        jwtUtil = mock(JwtUtil.class);
        localAuthUseCase = mock(LocalAuthUseCase.class);

        authController = new AuthController(googleAuthUseCase, jwtUtil, localAuthUseCase);
    }

    @Test
    void shouldReturnTokenWhenGoogleTokenIsValid() {
        // Given
        String idToken = "valid-token";
        User expectedUser = new User(
                UUID.randomUUID(),
                "user10",
                "user10@gmail.com",
                null,
                LocalDateTime.now(),
                AuthProvider.GOOGLE
        );

        when(googleAuthUseCase.authenticate(idToken)).thenReturn(expectedUser);
        when(jwtUtil.generateToken(eq(expectedUser.getEmail()), anyMap()))
                .thenReturn("jwt-token");

        // When
        ResponseEntity<Map<String, String>> response = authController.loginWithGoogle(idToken);

        // Then
        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody()).containsKey("token");
        assertThat(response.getBody().get("token")).isEqualTo("jwt-token");

        verify(googleAuthUseCase).authenticate(idToken);
        verify(jwtUtil).generateToken(eq(expectedUser.getEmail()), anyMap());
    }

}

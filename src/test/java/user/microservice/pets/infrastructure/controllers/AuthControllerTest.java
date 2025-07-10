package user.microservice.pets.infrastructure.controllers;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import user.microservice.pets.domain.enums.AuthProvider;
import user.microservice.pets.domain.model.User;
import user.microservice.pets.domain.ports.in.GoogleAuthUseCase;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

public class AuthControllerTest {

    private GoogleAuthUseCase googleAuthUseCase;
    private AuthController authController;

    @BeforeEach
    void setup() {
        googleAuthUseCase = mock(GoogleAuthUseCase.class);
        authController = new AuthController(googleAuthUseCase);
    }

    @Test
    void shouldReturnUserWhenTokenIsValid() {
        //Given
        String idToken = "valid-token";
        User expectedUser = new User(
                UUID.randomUUID(),
                "lsantiago",
                "lsantiago07@gmail.com",
                null,
                LocalDateTime.now(),
                AuthProvider.GOOGLE
        );

        when(googleAuthUseCase.authenticate(idToken)).thenReturn(expectedUser);

        //When
        ResponseEntity<User> response = authController.loginWithGoogle(idToken);

        //Then
        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody()).isEqualTo(expectedUser);

        verify(googleAuthUseCase).authenticate(idToken);
    }
}

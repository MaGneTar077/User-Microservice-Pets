package user.microservice.pets.infrastructure.controllers;

import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import user.microservice.pets.application.services.LogoutService;
import user.microservice.pets.infrastructure.security.JwtUtil;

import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("LogoutService - Unit Tests")
class LogoutServiceTest {

    @Mock
    private JwtUtil jwtUtil;

    @InjectMocks
    private LogoutService logoutService;

    @Test
    void shouldInvalidateTokenOnLogout() {
        // Given
        String token = "fake.jwt.token";

        Claims claims = org.mockito.Mockito.mock(Claims.class);
        when(claims.getExpiration()).thenReturn(new Date(System.currentTimeMillis() + 60000));
        when(claims.getSubject()).thenReturn("user@test.com");
        when(jwtUtil.validateToken(token)).thenReturn(claims);

        // When
        logoutService.logout(token);

        // Then
        assertThat(logoutService.isTokenInvalid(token)).isTrue();
    }

    @Test
    void shouldReturnFalseWhenTokenWasNotLoggedOut() {
        // Given
        String token = "non-invalidated-token";

        // When
        boolean result = logoutService.isTokenInvalid(token);

        // Then
        assertThat(result).isFalse();
    }
}

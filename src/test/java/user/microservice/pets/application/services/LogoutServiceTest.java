package user.microservice.pets.application.services;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("LogoutService - Unit Tests")
class LogoutServiceTest {

    @Test
    void shouldInvalidateTokenOnLogout() {
        // Given
        LogoutService logoutService = new LogoutService();
        String token = "fake-jwt-token";

        // When
        logoutService.logout(token);

        // Then
        assertThat(logoutService.isTokenInvalid(token)).isTrue();
    }

    @Test
    void shouldReturnFalseWhenTokenWasNotLoggedOut() {
        // Given
        LogoutService logoutService = new LogoutService();
        String token = "non-invalidated-token";

        // When
        boolean result = logoutService.isTokenInvalid(token);

        // Then
        assertThat(result).isFalse();
    }
}

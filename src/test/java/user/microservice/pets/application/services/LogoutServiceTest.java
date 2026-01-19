package user.microservice.pets.application.services;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.MalformedJwtException;
import io.jsonwebtoken.security.SignatureException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import user.microservice.pets.domain.exceptions.InvalidTokenException;
import user.microservice.pets.infrastructure.security.JwtUtil;

import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("LogoutService - Unit Tests")
class LogoutServiceTest {

    @Mock
    private JwtUtil jwtUtil;

    @InjectMocks
    private LogoutService logoutService;

    private String validToken;
    private Claims mockClaims;

    @BeforeEach
    void setUp() {
        validToken = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiJ0ZXN0QGV4YW1wbGUuY29tIn0.test";
        mockClaims = mock(Claims.class);
    }

    @Test
    @DisplayName("Should invalidate valid token on logout")
    void shouldInvalidateTokenOnLogout() {
        // Given
        Date futureExpiration = new Date(System.currentTimeMillis() + 3600000); // 1 hora en el futuro
        when(jwtUtil.validateToken(validToken)).thenReturn(mockClaims);
        when(mockClaims.getExpiration()).thenReturn(futureExpiration);
        when(mockClaims.getSubject()).thenReturn("test@example.com");

        // When
        logoutService.logout(validToken);

        // Then
        assertThat(logoutService.isTokenInvalid(validToken)).isTrue();
        verify(jwtUtil).validateToken(validToken);
    }

    @Test
    @DisplayName("Should return false when token was not logged out")
    void shouldReturnFalseWhenTokenWasNotLoggedOut() {
        // Given
        String nonInvalidatedToken = "non-invalidated-token";

        // When
        boolean result = logoutService.isTokenInvalid(nonInvalidatedToken);

        // Then
        assertThat(result).isFalse();
    }

    @Test
    @DisplayName("Should throw exception when token is null")
    void shouldThrowExceptionWhenTokenIsNull() {
        // When & Then
        assertThatThrownBy(() -> logoutService.logout(null))
                .isInstanceOf(InvalidTokenException.class)
                .hasMessage("Token cannot be empty");

        verifyNoInteractions(jwtUtil);
    }

    @Test
    @DisplayName("Should throw exception when token is empty")
    void shouldThrowExceptionWhenTokenIsEmpty() {
        // When & Then
        assertThatThrownBy(() -> logoutService.logout(""))
                .isInstanceOf(InvalidTokenException.class)
                .hasMessage("Token cannot be empty");

        verifyNoInteractions(jwtUtil);
    }

    @Test
    @DisplayName("Should throw exception when token is blank")
    void shouldThrowExceptionWhenTokenIsBlank() {
        // When & Then
        assertThatThrownBy(() -> logoutService.logout("   "))
                .isInstanceOf(InvalidTokenException.class)
                .hasMessage("Token cannot be empty");

        verifyNoInteractions(jwtUtil);
    }

    @Test
    @DisplayName("Should throw exception when token format is invalid")
    void shouldThrowExceptionWhenTokenFormatIsInvalid() {
        // Given
        String invalidFormatToken = "invalid.token"; // Solo 2 partes en lugar de 3

        // When & Then
        assertThatThrownBy(() -> logoutService.logout(invalidFormatToken))
                .isInstanceOf(InvalidTokenException.class)
                .hasMessage("Invalid token format");

        verifyNoInteractions(jwtUtil);
    }

    @Test
    @DisplayName("Should throw exception when token is already expired")
    void shouldThrowExceptionWhenTokenIsAlreadyExpired() {
        // Given
        Date pastExpiration = new Date(System.currentTimeMillis() - 3600000); // 1 hora en el pasado
        when(jwtUtil.validateToken(validToken)).thenReturn(mockClaims);
        when(mockClaims.getExpiration()).thenReturn(pastExpiration);

        // When & Then
        assertThatThrownBy(() -> logoutService.logout(validToken))
                .isInstanceOf(InvalidTokenException.class)
                .hasMessage("Token is already expired");

        verify(jwtUtil).validateToken(validToken);
    }

    @Test
    @DisplayName("Should throw exception when token is already invalidated")
    void shouldThrowExceptionWhenTokenIsAlreadyInvalidated() {
        // Given
        Date futureExpiration = new Date(System.currentTimeMillis() + 3600000);
        when(jwtUtil.validateToken(validToken)).thenReturn(mockClaims);
        when(mockClaims.getExpiration()).thenReturn(futureExpiration);
        when(mockClaims.getSubject()).thenReturn("test@example.com");

        // First logout
        logoutService.logout(validToken);

        // When & Then - Second logout attempt
        assertThatThrownBy(() -> logoutService.logout(validToken))
                .isInstanceOf(InvalidTokenException.class)
                .hasMessage("Token is already invalidated");
    }

    @Test
    @DisplayName("Should throw exception when JWT is expired during validation")
    void shouldThrowExceptionWhenJwtIsExpired() {
        // Given
        when(jwtUtil.validateToken(validToken)).thenThrow(new ExpiredJwtException(null, null, "Token expired"));

        // When & Then
        assertThatThrownBy(() -> logoutService.logout(validToken))
                .isInstanceOf(InvalidTokenException.class)
                .hasMessage("Token is expired");

        verify(jwtUtil).validateToken(validToken);
    }

    @Test
    @DisplayName("Should throw exception when JWT is malformed")
    void shouldThrowExceptionWhenJwtIsMalformed() {
        // Given
        when(jwtUtil.validateToken(validToken)).thenThrow(new MalformedJwtException("Malformed JWT"));

        // When & Then
        assertThatThrownBy(() -> logoutService.logout(validToken))
                .isInstanceOf(InvalidTokenException.class)
                .hasMessage("Malformed token");

        verify(jwtUtil).validateToken(validToken);
    }

    @Test
    @DisplayName("Should throw exception when JWT signature is invalid")
    void shouldThrowExceptionWhenJwtSignatureIsInvalid() {
        // Given
        when(jwtUtil.validateToken(validToken)).thenThrow(new SignatureException("Invalid signature"));

        // When & Then
        assertThatThrownBy(() -> logoutService.logout(validToken))
                .isInstanceOf(InvalidTokenException.class)
                .hasMessage("Invalid token signature");

        verify(jwtUtil).validateToken(validToken);
    }

    @Test
    @DisplayName("Should throw exception for any other JWT validation error")
    void shouldThrowExceptionForGenericJwtError() {
        // Given
        when(jwtUtil.validateToken(validToken)).thenThrow(new RuntimeException("Unexpected error"));

        // When & Then
        assertThatThrownBy(() -> logoutService.logout(validToken))
                .isInstanceOf(InvalidTokenException.class)
                .hasMessage("Invalid token");

        verify(jwtUtil).validateToken(validToken);
    }

    @Test
    @DisplayName("Should return false for null token in isTokenInvalid")
    void shouldReturnFalseForNullTokenInIsTokenInvalid() {
        // When
        boolean result = logoutService.isTokenInvalid(null);

        // Then
        assertThat(result).isFalse();
    }

    @Test
    @DisplayName("Should return false for empty token in isTokenInvalid")
    void shouldReturnFalseForEmptyTokenInIsTokenInvalid() {
        // When
        boolean result = logoutService.isTokenInvalid("");

        // Then
        assertThat(result).isFalse();
    }

    @Test
    @DisplayName("Should return false for blank token in isTokenInvalid")
    void shouldReturnFalseForBlankTokenInIsTokenInvalid() {
        // When
        boolean result = logoutService.isTokenInvalid("   ");

        // Then
        assertThat(result).isFalse();
    }

    @Test
    @DisplayName("Should return blacklist size correctly")
    void shouldReturnBlacklistSizeCorrectly() {
        // Given
        Date futureExpiration = new Date(System.currentTimeMillis() + 3600000);
        String token1 = "token1.valid.token";
        String token2 = "token2.valid.token";

        when(jwtUtil.validateToken(anyString())).thenReturn(mockClaims);
        when(mockClaims.getExpiration()).thenReturn(futureExpiration);
        when(mockClaims.getSubject()).thenReturn("test@example.com");

        // When
        logoutService.logout(token1);
        logoutService.logout(token2);

        // Then
        assertThat(logoutService.getBlacklistSize()).isEqualTo(2);
    }

    @Test
    @DisplayName("Should handle multiple different tokens")
    void shouldHandleMultipleDifferentTokens() {
        // Given
        Date futureExpiration = new Date(System.currentTimeMillis() + 3600000);
        String token1 = "token1.valid.token";
        String token2 = "token2.valid.token";
        String token3 = "token3.valid.token";

        when(jwtUtil.validateToken(anyString())).thenReturn(mockClaims);
        when(mockClaims.getExpiration()).thenReturn(futureExpiration);
        when(mockClaims.getSubject()).thenReturn("test@example.com");

        // When
        logoutService.logout(token1);
        logoutService.logout(token2);
        logoutService.logout(token3);

        // Then
        assertThat(logoutService.isTokenInvalid(token1)).isTrue();
        assertThat(logoutService.isTokenInvalid(token2)).isTrue();
        assertThat(logoutService.isTokenInvalid(token3)).isTrue();
        assertThat(logoutService.getBlacklistSize()).isEqualTo(3);
    }
}
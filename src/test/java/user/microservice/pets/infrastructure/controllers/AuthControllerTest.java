package user.microservice.pets.infrastructure.controllers;

import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;
import user.microservice.pets.application.dto.LoginRequest;
import user.microservice.pets.application.dto.LogoutRequest;
import user.microservice.pets.application.dto.RefreshTokenRequest;
import user.microservice.pets.application.services.LogoutService;
import user.microservice.pets.application.services.RefreshTokenService;
import user.microservice.pets.domain.enums.AuthProvider;
import user.microservice.pets.domain.enums.PlatformRole;
import user.microservice.pets.domain.model.User;
import user.microservice.pets.domain.ports.in.GoogleAuthUseCase;
import user.microservice.pets.domain.ports.in.LocalAuthUseCase;
import user.microservice.pets.domain.ports.in.PublishAuthEventUseCase;
import user.microservice.pets.domain.ports.out.UserRepositoryPort;
import user.microservice.pets.infrastructure.security.JwtUtil;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("AuthController - Unit Tests")
class AuthControllerTest {

    @Mock
    private GoogleAuthUseCase googleAuthUseCase;

    @Mock
    private JwtUtil jwtUtil;

    @Mock
    private LocalAuthUseCase localAuthUseCase;

    @Mock
    private LogoutService logoutService;

    @Mock
    private PublishAuthEventUseCase publishAuthEventUseCase;

    @Mock
    private RefreshTokenService refreshTokenService;

    @Mock
    private UserRepositoryPort userRepositoryPort;

    private AuthController authController;

    @BeforeEach
    void setUp() {
        authController = new AuthController(
                googleAuthUseCase, jwtUtil, localAuthUseCase, logoutService,
                publishAuthEventUseCase, refreshTokenService, userRepositoryPort);
    }

    private User buildUser(UUID userId) {
        return User.builder()
                .id(userId)
                .email("user@test.com")
                .username("testuser")
                .authProvider(AuthProvider.LOCAL)
                .createdAt(LocalDateTime.now())
                .emailVerified(true)
                .platformRole(PlatformRole.USER)
                .build();
    }

    @Test
    @DisplayName("loginLocal should issue a JWT whose subject is the user's UUID, plus a refreshToken and expiresIn")
    void loginLocalShouldIssueTokenWithUserIdAsSubject() {
        UUID userId = UUID.randomUUID();
        User user = buildUser(userId);

        LoginRequest request = new LoginRequest();
        request.setEmail("user@test.com");
        request.setPassword("Password1!");

        when(localAuthUseCase.login("user@test.com", "Password1!")).thenReturn(user);
        when(jwtUtil.generateToken(anyString(), any())).thenReturn("fake.jwt.token");
        when(jwtUtil.getAccessTokenTtlSeconds()).thenReturn(3600L);
        when(refreshTokenService.issue(userId)).thenReturn("fake-refresh-token");

        ResponseEntity<Map<String, Object>> response = authController.loginLocal(request);

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getBody())
                .containsEntry("token", "fake.jwt.token")
                .containsEntry("refreshToken", "fake-refresh-token")
                .containsEntry("expiresIn", 3600L);

        ArgumentCaptor<String> subjectCaptor = ArgumentCaptor.forClass(String.class);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> claimsCaptor = ArgumentCaptor.forClass(Map.class);
        verify(jwtUtil).generateToken(subjectCaptor.capture(), claimsCaptor.capture());

        assertThat(subjectCaptor.getValue()).isEqualTo(userId.toString());
        assertThat(claimsCaptor.getValue())
                .containsEntry("id", userId.toString())
                .containsEntry("email", "user@test.com")
                .containsEntry("username", "testuser")
                .containsEntry("provider", "LOCAL")
                .containsEntry("email_verified", true)
                .containsEntry("platform_role", "USER")
                .containsEntry("ctx", "USER");
    }

    @Test
    @DisplayName("refresh should rotate the refresh token and issue a new access token for its owner")
    void refreshShouldRotateTokenAndIssueNewAccessToken() {
        UUID userId = UUID.randomUUID();
        User user = buildUser(userId);

        when(refreshTokenService.rotate("old-refresh-token"))
                .thenReturn(new RefreshTokenService.RotatedRefreshToken(userId, "new-refresh-token"));
        when(userRepositoryPort.findById(userId)).thenReturn(Optional.of(user));
        when(jwtUtil.generateToken(anyString(), any())).thenReturn("new.jwt.token");
        when(jwtUtil.getAccessTokenTtlSeconds()).thenReturn(3600L);

        ResponseEntity<Map<String, Object>> response =
                authController.refresh(new RefreshTokenRequest("old-refresh-token"));

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getBody())
                .containsEntry("token", "new.jwt.token")
                .containsEntry("refreshToken", "new-refresh-token")
                .containsEntry("expiresIn", 3600L);

        // El refresh no es un nuevo login: no debe publicar un evento USER_LOGIN.
        verify(publishAuthEventUseCase, never()).publish(any());
    }

    @Test
    @DisplayName("logout should revoke the access token and respond with success message")
    void logoutShouldInvalidateTokenAndReturnOk() {
        Claims claims = mock(Claims.class);
        when(claims.get("email", String.class)).thenReturn("user@test.com");
        when(claims.get("id", String.class)).thenReturn(UUID.randomUUID().toString());
        when(logoutService.logout("valid.jwt.token")).thenReturn(claims);

        ResponseEntity<Map<String, String>> response =
                authController.logout("Bearer valid.jwt.token", null);

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getBody()).containsEntry("message", "Logout successful");
        verify(logoutService).logout("valid.jwt.token");
        verify(refreshTokenService, never()).revoke(any());
    }

    @Test
    @DisplayName("logout should also revoke the refresh token when it comes in the request body")
    void logoutShouldRevokeRefreshTokenWhenPresentInBody() {
        Claims claims = mock(Claims.class);
        when(claims.get("email", String.class)).thenReturn("user@test.com");
        when(claims.get("id", String.class)).thenReturn(UUID.randomUUID().toString());
        when(logoutService.logout("valid.jwt.token")).thenReturn(claims);

        ResponseEntity<Map<String, String>> response = authController.logout(
                "Bearer valid.jwt.token", new LogoutRequest("some-refresh-token"));

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        verify(refreshTokenService).revoke("some-refresh-token");
    }
}

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
import org.springframework.security.core.Authentication;
import user.microservice.pets.application.dto.LoginRequest;
import user.microservice.pets.application.dto.LogoutRequest;
import user.microservice.pets.application.dto.PreviousRefreshTokenRequest;
import user.microservice.pets.application.dto.RefreshTokenRequest;
import user.microservice.pets.application.services.LogoutService;
import user.microservice.pets.application.services.RefreshTokenService;
import user.microservice.pets.domain.enums.AuthProvider;
import user.microservice.pets.domain.enums.PlatformRole;
import user.microservice.pets.domain.enums.TokenContext;
import user.microservice.pets.domain.exceptions.NotVeterinaryMemberException;
import user.microservice.pets.domain.exceptions.VeterinaryMembershipRevokedException;
import user.microservice.pets.domain.exceptions.VeterinaryRejectedException;
import user.microservice.pets.domain.exceptions.VeterinaryServiceUnavailableException;
import user.microservice.pets.domain.model.RefreshToken;
import user.microservice.pets.domain.model.User;
import user.microservice.pets.domain.model.VeterinaryMembership;
import user.microservice.pets.domain.ports.in.GoogleAuthUseCase;
import user.microservice.pets.domain.ports.in.LocalAuthUseCase;
import user.microservice.pets.domain.ports.in.PublishAuthEventUseCase;
import user.microservice.pets.domain.ports.out.UserRepositoryPort;
import user.microservice.pets.domain.ports.out.VeterinaryMembershipPort;
import user.microservice.pets.infrastructure.security.JwtUtil;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
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

    @Mock
    private VeterinaryMembershipPort veterinaryMembershipPort;

    private AuthController authController;

    @BeforeEach
    void setUp() {
        authController = new AuthController(
                googleAuthUseCase, jwtUtil, localAuthUseCase, logoutService,
                publishAuthEventUseCase, refreshTokenService, userRepositoryPort, veterinaryMembershipPort);
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

    private RefreshToken buildStoredToken(UUID userId, TokenContext context, UUID veterinaryId) {
        return RefreshToken.builder()
                .id(UUID.randomUUID())
                .userId(userId)
                .tokenHash("some-hash")
                .context(context)
                .veterinaryId(veterinaryId)
                .expiresAt(LocalDateTime.now().plusDays(10))
                .createdAt(LocalDateTime.now())
                .build();
    }

    private Authentication authenticationFor(UUID userId) {
        Authentication authentication = mock(Authentication.class);
        when(authentication.getName()).thenReturn(userId.toString());
        return authentication;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> capturedClaims() {
        ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
        verify(jwtUtil).generateToken(anyString(), captor.capture());
        return captor.getValue();
    }

    // ---- Login ----

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

        assertThat(capturedClaims())
                .containsEntry("id", userId.toString())
                .containsEntry("email", "user@test.com")
                .containsEntry("username", "testuser")
                .containsEntry("provider", "LOCAL")
                .containsEntry("email_verified", true)
                .containsEntry("platform_role", "USER")
                .containsEntry("ctx", "USER");
    }

    // ---- /auth/refresh (context=USER) ----

    @Test
    @DisplayName("refresh with a USER-context token should rotate it and never call Veterinary")
    void refreshUserContextShouldRotateWithoutCallingVeterinary() {
        UUID userId = UUID.randomUUID();
        User user = buildUser(userId);
        RefreshToken existing = buildStoredToken(userId, TokenContext.USER, null);

        when(refreshTokenService.validate("old-refresh-token")).thenReturn(existing);
        when(refreshTokenService.rotate(existing))
                .thenReturn(new RefreshTokenService.RotatedRefreshToken(userId, "new-refresh-token", TokenContext.USER, null));
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
        assertThat(response.getBody()).doesNotContainKey("veterinary");

        verify(veterinaryMembershipPort, never()).getMembership(any(), any());
        // El refresh no es un nuevo login: no debe publicar un evento USER_LOGIN.
        verify(publishAuthEventUseCase, never()).publish(any());
    }

    // ---- /auth/refresh (context=VETERINARY, USER-04) ----

    @Test
    @DisplayName("refresh with an active clinic membership should rotate and issue an updated clinic token")
    void refreshVeterinaryContextActiveShouldRotateWithUpdatedRole() {
        UUID userId = UUID.randomUUID();
        UUID veterinaryId = UUID.randomUUID();
        UUID employeeId = UUID.randomUUID();
        User user = buildUser(userId);
        RefreshToken existing = buildStoredToken(userId, TokenContext.VETERINARY, veterinaryId);

        VeterinaryMembership activeMembership =
                new VeterinaryMembership(true, employeeId, "VETERINARIAN", true, true, "ACTIVE", "ACTIVE");

        when(refreshTokenService.validate("old-vet-refresh")).thenReturn(existing);
        when(veterinaryMembershipPort.getMembership(veterinaryId, userId)).thenReturn(activeMembership);
        when(refreshTokenService.rotate(existing)).thenReturn(
                new RefreshTokenService.RotatedRefreshToken(userId, "new-vet-refresh", TokenContext.VETERINARY, veterinaryId));
        when(userRepositoryPort.findById(userId)).thenReturn(Optional.of(user));
        when(jwtUtil.generateToken(anyString(), any())).thenReturn("new.vet.jwt.token");
        when(jwtUtil.getAccessTokenTtlSeconds()).thenReturn(3600L);

        ResponseEntity<Map<String, Object>> response =
                authController.refresh(new RefreshTokenRequest("old-vet-refresh"));

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getBody()).containsEntry("refreshToken", "new-vet-refresh");

        assertThat(capturedClaims())
                .containsEntry("ctx", "VETERINARY")
                .containsEntry("vet_id", veterinaryId.toString())
                .containsEntry("vet_role", "VETERINARIAN")
                .containsEntry("employee_id", employeeId.toString())
                .containsEntry("vet_licensed", true)
                .containsEntry("vet_status", "ACTIVE");

        verify(refreshTokenService, never()).revoke(any(RefreshToken.class));
    }

    @Test
    @DisplayName("refresh after the employee was deactivated should revoke it and respond VETERINARY_MEMBERSHIP_REVOKED")
    void refreshVeterinaryContextRevokedMembershipShouldFail() {
        UUID userId = UUID.randomUUID();
        UUID veterinaryId = UUID.randomUUID();
        RefreshToken existing = buildStoredToken(userId, TokenContext.VETERINARY, veterinaryId);

        VeterinaryMembership inactiveMembership =
                new VeterinaryMembership(true, UUID.randomUUID(), "ASSISTANT", false, true, "ACTIVE", "ACTIVE");

        when(refreshTokenService.validate("old-vet-refresh")).thenReturn(existing);
        when(veterinaryMembershipPort.getMembership(veterinaryId, userId)).thenReturn(inactiveMembership);

        assertThatThrownBy(() -> authController.refresh(new RefreshTokenRequest("old-vet-refresh")))
                .isInstanceOf(VeterinaryMembershipRevokedException.class);

        verify(refreshTokenService).revoke(existing);
        verify(refreshTokenService, never()).rotate(any(RefreshToken.class));
    }

    @Test
    @DisplayName("refresh with Veterinary down should return 503 without revoking anything")
    void refreshVeterinaryContextUnavailableShouldNotRevoke() {
        UUID userId = UUID.randomUUID();
        UUID veterinaryId = UUID.randomUUID();
        RefreshToken existing = buildStoredToken(userId, TokenContext.VETERINARY, veterinaryId);

        when(refreshTokenService.validate("old-vet-refresh")).thenReturn(existing);
        when(veterinaryMembershipPort.getMembership(veterinaryId, userId))
                .thenThrow(new VeterinaryServiceUnavailableException("Veterinary service returned 503"));

        assertThatThrownBy(() -> authController.refresh(new RefreshTokenRequest("old-vet-refresh")))
                .isInstanceOf(VeterinaryServiceUnavailableException.class);

        verify(refreshTokenService, never()).revoke(any(RefreshToken.class));
        verify(refreshTokenService, never()).rotate(any(RefreshToken.class));
    }

    // ---- /auth/context/veterinary/{id} (USER-03) ----

    @Test
    @DisplayName("context/veterinary should issue a clinic token when the user is an active member")
    void getVeterinaryContextTokenActiveMemberShouldSucceed() {
        UUID userId = UUID.randomUUID();
        UUID veterinaryId = UUID.randomUUID();
        UUID employeeId = UUID.randomUUID();
        User user = buildUser(userId);
        Authentication authentication = authenticationFor(userId);

        VeterinaryMembership membership =
                new VeterinaryMembership(true, employeeId, "ADMIN", true, true, "ACTIVE", "ACTIVE");

        when(userRepositoryPort.findById(userId)).thenReturn(Optional.of(user));
        when(veterinaryMembershipPort.getMembership(veterinaryId, userId)).thenReturn(membership);
        when(refreshTokenService.issueVeterinaryContext(userId, veterinaryId)).thenReturn("vet-refresh-token");
        when(jwtUtil.generateToken(anyString(), any())).thenReturn("vet.jwt.token");
        when(jwtUtil.getAccessTokenTtlSeconds()).thenReturn(3600L);

        ResponseEntity<Map<String, Object>> response =
                authController.getVeterinaryContextToken(veterinaryId, authentication, null);

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getBody())
                .containsEntry("token", "vet.jwt.token")
                .containsEntry("refreshToken", "vet-refresh-token")
                .containsEntry("expiresIn", 3600L);

        @SuppressWarnings("unchecked")
        Map<String, Object> veterinaryInfo = (Map<String, Object>) response.getBody().get("veterinary");
        assertThat(veterinaryInfo)
                .containsEntry("id", veterinaryId)
                .containsEntry("role", "ADMIN")
                .containsEntry("status", "ACTIVE");

        assertThat(capturedClaims())
                .containsEntry("ctx", "VETERINARY")
                .containsEntry("vet_id", veterinaryId.toString())
                .containsEntry("vet_role", "ADMIN")
                .containsEntry("employee_id", employeeId.toString())
                .containsEntry("vet_licensed", true)
                .containsEntry("vet_status", "ACTIVE")
                // Debe conservar TODOS los claims del token base.
                .containsEntry("id", userId.toString())
                .containsEntry("email", "user@test.com")
                .containsEntry("platform_role", "USER")
                .containsEntry("email_verified", true);
    }

    @Test
    @DisplayName("context/veterinary should issue a token for a SUSPENDED clinic too (only REJECTED is blocked)")
    void getVeterinaryContextTokenSuspendedClinicShouldStillSucceed() {
        UUID userId = UUID.randomUUID();
        UUID veterinaryId = UUID.randomUUID();
        User user = buildUser(userId);
        Authentication authentication = authenticationFor(userId);

        VeterinaryMembership membership =
                new VeterinaryMembership(true, UUID.randomUUID(), "ADMIN", true, true, "SUSPENDED", "ACTIVE");

        when(userRepositoryPort.findById(userId)).thenReturn(Optional.of(user));
        when(veterinaryMembershipPort.getMembership(veterinaryId, userId)).thenReturn(membership);
        when(refreshTokenService.issueVeterinaryContext(userId, veterinaryId)).thenReturn("vet-refresh-token");
        when(jwtUtil.generateToken(anyString(), any())).thenReturn("vet.jwt.token");
        when(jwtUtil.getAccessTokenTtlSeconds()).thenReturn(3600L);

        ResponseEntity<Map<String, Object>> response =
                authController.getVeterinaryContextToken(veterinaryId, authentication, null);

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(capturedClaims()).containsEntry("vet_status", "SUSPENDED");
    }

    @Test
    @DisplayName("context/veterinary should reject a non-member with NOT_A_VETERINARY_MEMBER")
    void getVeterinaryContextTokenNotMemberShouldFail() {
        UUID userId = UUID.randomUUID();
        UUID veterinaryId = UUID.randomUUID();
        Authentication authentication = authenticationFor(userId);

        VeterinaryMembership notMember =
                new VeterinaryMembership(false, null, null, null, null, null, null);

        when(userRepositoryPort.findById(userId)).thenReturn(Optional.of(buildUser(userId)));
        when(veterinaryMembershipPort.getMembership(veterinaryId, userId)).thenReturn(notMember);

        assertThatThrownBy(() -> authController.getVeterinaryContextToken(veterinaryId, authentication, null))
                .isInstanceOf(NotVeterinaryMemberException.class);

        verify(refreshTokenService, never()).issueVeterinaryContext(any(), any());
    }

    @Test
    @DisplayName("context/veterinary should reject an inactive member with NOT_A_VETERINARY_MEMBER")
    void getVeterinaryContextTokenInactiveMemberShouldFail() {
        UUID userId = UUID.randomUUID();
        UUID veterinaryId = UUID.randomUUID();
        Authentication authentication = authenticationFor(userId);

        VeterinaryMembership inactive =
                new VeterinaryMembership(true, UUID.randomUUID(), "ASSISTANT", false, true, "ACTIVE", "ACTIVE");

        when(userRepositoryPort.findById(userId)).thenReturn(Optional.of(buildUser(userId)));
        when(veterinaryMembershipPort.getMembership(veterinaryId, userId)).thenReturn(inactive);

        assertThatThrownBy(() -> authController.getVeterinaryContextToken(veterinaryId, authentication, null))
                .isInstanceOf(NotVeterinaryMemberException.class);
    }

    @Test
    @DisplayName("context/veterinary should reject a REJECTED clinic with VETERINARY_REJECTED")
    void getVeterinaryContextTokenRejectedClinicShouldFail() {
        UUID userId = UUID.randomUUID();
        UUID veterinaryId = UUID.randomUUID();
        Authentication authentication = authenticationFor(userId);

        VeterinaryMembership rejected =
                new VeterinaryMembership(true, UUID.randomUUID(), "ADMIN", true, true, "REJECTED", null);

        when(userRepositoryPort.findById(userId)).thenReturn(Optional.of(buildUser(userId)));
        when(veterinaryMembershipPort.getMembership(veterinaryId, userId)).thenReturn(rejected);

        assertThatThrownBy(() -> authController.getVeterinaryContextToken(veterinaryId, authentication, null))
                .isInstanceOf(VeterinaryRejectedException.class);

        verify(refreshTokenService, never()).issueVeterinaryContext(any(), any());
    }

    @Test
    @DisplayName("context/veterinary should return 503 when Veterinary is down, without issuing any token")
    void getVeterinaryContextTokenUnavailableShouldFail() {
        UUID userId = UUID.randomUUID();
        UUID veterinaryId = UUID.randomUUID();
        Authentication authentication = authenticationFor(userId);

        when(userRepositoryPort.findById(userId)).thenReturn(Optional.of(buildUser(userId)));
        when(veterinaryMembershipPort.getMembership(veterinaryId, userId))
                .thenThrow(new VeterinaryServiceUnavailableException("Veterinary service unreachable"));

        assertThatThrownBy(() -> authController.getVeterinaryContextToken(veterinaryId, authentication, null))
                .isInstanceOf(VeterinaryServiceUnavailableException.class);

        verify(refreshTokenService, never()).issueVeterinaryContext(any(), any());
    }

    @Test
    @DisplayName("context/veterinary should revoke the previous clinic's refresh token when provided")
    void getVeterinaryContextTokenShouldRevokePreviousRefreshToken() {
        UUID userId = UUID.randomUUID();
        UUID veterinaryId = UUID.randomUUID();
        Authentication authentication = authenticationFor(userId);

        VeterinaryMembership membership =
                new VeterinaryMembership(true, UUID.randomUUID(), "ADMIN", true, true, "ACTIVE", "ACTIVE");

        when(userRepositoryPort.findById(userId)).thenReturn(Optional.of(buildUser(userId)));
        when(veterinaryMembershipPort.getMembership(veterinaryId, userId)).thenReturn(membership);
        when(refreshTokenService.issueVeterinaryContext(userId, veterinaryId)).thenReturn("vet-refresh-token");
        when(jwtUtil.generateToken(anyString(), any())).thenReturn("vet.jwt.token");
        when(jwtUtil.getAccessTokenTtlSeconds()).thenReturn(3600L);

        authController.getVeterinaryContextToken(
                veterinaryId, authentication, new PreviousRefreshTokenRequest("old-clinic-refresh"));

        verify(refreshTokenService).revoke("old-clinic-refresh");
    }

    // ---- /auth/context/user (USER-05) ----

    @Test
    @DisplayName("context/user should issue a normal ctx=USER token")
    void getUserContextTokenShouldIssueUserToken() {
        UUID userId = UUID.randomUUID();
        User user = buildUser(userId);
        Authentication authentication = authenticationFor(userId);

        when(userRepositoryPort.findById(userId)).thenReturn(Optional.of(user));
        when(refreshTokenService.issue(userId)).thenReturn("user-refresh-token");
        when(jwtUtil.generateToken(anyString(), any())).thenReturn("user.jwt.token");
        when(jwtUtil.getAccessTokenTtlSeconds()).thenReturn(3600L);

        ResponseEntity<Map<String, Object>> response = authController.getUserContextToken(authentication, null);

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getBody())
                .containsEntry("token", "user.jwt.token")
                .containsEntry("refreshToken", "user-refresh-token")
                .doesNotContainKey("veterinary");

        assertThat(capturedClaims()).containsEntry("ctx", "USER");
    }

    @Test
    @DisplayName("context/user should revoke the clinic refresh token when provided")
    void getUserContextTokenShouldRevokePreviousClinicRefreshToken() {
        UUID userId = UUID.randomUUID();
        Authentication authentication = authenticationFor(userId);

        when(userRepositoryPort.findById(userId)).thenReturn(Optional.of(buildUser(userId)));
        when(refreshTokenService.issue(userId)).thenReturn("user-refresh-token");
        when(jwtUtil.generateToken(anyString(), any())).thenReturn("user.jwt.token");
        when(jwtUtil.getAccessTokenTtlSeconds()).thenReturn(3600L);

        authController.getUserContextToken(authentication, new PreviousRefreshTokenRequest("old-clinic-refresh"));

        verify(refreshTokenService).revoke("old-clinic-refresh");
    }

    // ---- Logout ----

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
        verify(refreshTokenService, never()).revoke(anyString());
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

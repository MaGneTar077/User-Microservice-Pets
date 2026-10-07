package user.microservice.pets.infrastructure.controllers;

import io.jsonwebtoken.Claims;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import user.microservice.pets.application.dto.AuthEvent;
import user.microservice.pets.application.dto.GoogleTokenRequest;
import user.microservice.pets.application.dto.LoginRequest;
import user.microservice.pets.application.dto.LogoutRequest;
import user.microservice.pets.application.dto.PreviousRefreshTokenRequest;
import user.microservice.pets.application.dto.RefreshTokenRequest;
import user.microservice.pets.application.services.LogoutService;
import user.microservice.pets.application.services.RefreshTokenService;
import user.microservice.pets.domain.enums.TokenContext;
import user.microservice.pets.domain.exceptions.InvalidTokenException;
import user.microservice.pets.domain.exceptions.NotVeterinaryMemberException;
import user.microservice.pets.domain.exceptions.VeterinaryMembershipRevokedException;
import user.microservice.pets.domain.exceptions.VeterinaryRejectedException;
import user.microservice.pets.domain.model.RefreshToken;
import user.microservice.pets.domain.model.User;
import user.microservice.pets.domain.model.VeterinaryMembership;
import user.microservice.pets.domain.ports.in.GoogleAuthUseCase;
import user.microservice.pets.domain.ports.in.LocalAuthUseCase;
import user.microservice.pets.domain.ports.in.PublishAuthEventUseCase;
import user.microservice.pets.domain.ports.out.UserRepositoryPort;
import user.microservice.pets.domain.ports.out.VeterinaryMembershipPort;
import user.microservice.pets.infrastructure.security.JwtUtil;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private static final String VETERINARY_STATUS_REJECTED = "REJECTED";

    private final GoogleAuthUseCase googleAuthUseCase;
    private final JwtUtil jwtUtil;
    private final LocalAuthUseCase localAuthUseCase;
    private final LogoutService logoutService;
    private final PublishAuthEventUseCase publishAuthEventUseCase;
    private final RefreshTokenService refreshTokenService;
    private final UserRepositoryPort userRepositoryPort;
    private final VeterinaryMembershipPort veterinaryMembershipPort;

    @PostMapping("/google")
    public ResponseEntity<Map<String, Object>> loginWithGoogle(@RequestBody GoogleTokenRequest request) {
        if (request.idToken() == null || request.idToken().isBlank()) {
            throw new InvalidTokenException("Google idToken is required");
        }
        User user = googleAuthUseCase.authenticate(request.idToken().trim());
        return ResponseEntity.ok(buildLoginResponse(user));
    }

    @PostMapping("/local")
    public ResponseEntity<Map<String, Object>> loginLocal(@Valid @RequestBody LoginRequest request) {
        User user = localAuthUseCase.login(request.getEmail(), request.getPassword());
        return ResponseEntity.ok(buildLoginResponse(user));
    }

    @PostMapping("/refresh")
    public ResponseEntity<Map<String, Object>> refresh(@Valid @RequestBody RefreshTokenRequest request) {
        RefreshToken existing = refreshTokenService.validate(request.refreshToken());

        if (existing.getContext() == TokenContext.VETERINARY) {
            return refreshVeterinaryContext(existing);
        }

        RefreshTokenService.RotatedRefreshToken rotated = refreshTokenService.rotate(existing);
        User user = userRepositoryPort.findById(rotated.userId())
                .orElseThrow(() -> new InvalidTokenException("User not found"));

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("token", buildAccessToken(user));
        response.put("refreshToken", rotated.rawToken());
        response.put("expiresIn", jwtUtil.getAccessTokenTtlSeconds());
        return ResponseEntity.ok(response);
    }

    @PostMapping("/context/veterinary/{veterinaryId}")
    public ResponseEntity<Map<String, Object>> getVeterinaryContextToken(
            @PathVariable UUID veterinaryId,
            Authentication authentication,
            @RequestBody(required = false) PreviousRefreshTokenRequest previousContext) {

        UUID userId = authenticatedUserId(authentication);
        User user = userRepositoryPort.findById(userId)
                .orElseThrow(() -> new InvalidTokenException("User not found"));

        VeterinaryMembership membership = veterinaryMembershipPort.getMembership(veterinaryId, userId);
        requireUsableMembership(membership);

        revokePreviousContextIfPresent(previousContext);

        String accessToken = buildVeterinaryAccessToken(user, veterinaryId, membership);
        String refreshToken = refreshTokenService.issueVeterinaryContext(userId, veterinaryId);

        log.info("Veterinary context token issued for user {} on clinic {}", userId, veterinaryId);

        return ResponseEntity.ok(buildContextResponse(accessToken, refreshToken, veterinaryId, membership));
    }

    @PostMapping("/context/user")
    public ResponseEntity<Map<String, Object>> getUserContextToken(
            Authentication authentication,
            @RequestBody(required = false) PreviousRefreshTokenRequest previousContext) {

        UUID userId = authenticatedUserId(authentication);
        User user = userRepositoryPort.findById(userId)
                .orElseThrow(() -> new InvalidTokenException("User not found"));

        revokePreviousContextIfPresent(previousContext);

        String accessToken = buildAccessToken(user);
        String refreshToken = refreshTokenService.issue(userId);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("token", accessToken);
        response.put("refreshToken", refreshToken);
        response.put("expiresIn", jwtUtil.getAccessTokenTtlSeconds());
        return ResponseEntity.ok(response);
    }

    @PostMapping("/logout")
    public ResponseEntity<Map<String, String>> logout(
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @RequestBody(required = false) LogoutRequest request) {

        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            throw new InvalidTokenException("Authorization header must be 'Bearer <token>'");
        }

        String token = authHeader.substring(7).trim();
        Claims claims = logoutService.logout(token);

        if (request != null && request.refreshToken() != null && !request.refreshToken().isBlank()) {
            refreshTokenService.revoke(request.refreshToken());
        }

        // "email" (no "sub") es el claim estable: en tokens nuevos sub es el UUID del usuario.
        String email = claims.get("email", String.class);
        String id = claims.get("id", String.class);

        publishAuthEventUseCase.publish(AuthEvent.builder()
                .userId(id != null ? UUID.fromString(id) : null)
                .email(email)
                .eventType("USER_LOGOUT")
                .occurredAt(Instant.now())
                .build());

        log.info("User logged out: {}", email);
        return ResponseEntity.ok(Map.of("message", "Logout successful"));
    }

    private ResponseEntity<Map<String, Object>> refreshVeterinaryContext(RefreshToken existing) {
        VeterinaryMembership membership = veterinaryMembershipPort.getMembership(
                existing.getVeterinaryId(), existing.getUserId());

        if (!membership.isActiveMember()) {
            refreshTokenService.revoke(existing);
            throw new VeterinaryMembershipRevokedException();
        }

        RefreshTokenService.RotatedRefreshToken rotated = refreshTokenService.rotate(existing);
        User user = userRepositoryPort.findById(rotated.userId())
                .orElseThrow(() -> new InvalidTokenException("User not found"));

        String accessToken = buildVeterinaryAccessToken(user, existing.getVeterinaryId(), membership);
        return ResponseEntity.ok(
                buildContextResponse(accessToken, rotated.rawToken(), existing.getVeterinaryId(), membership));
    }

    private void requireUsableMembership(VeterinaryMembership membership) {
        if (!membership.isActiveMember()) {
            throw new NotVeterinaryMemberException();
        }
        if (VETERINARY_STATUS_REJECTED.equals(membership.veterinaryStatus())) {
            throw new VeterinaryRejectedException();
        }
    }

    private void revokePreviousContextIfPresent(PreviousRefreshTokenRequest previousContext) {
        if (previousContext != null && previousContext.refreshToken() != null
                && !previousContext.refreshToken().isBlank()) {
            refreshTokenService.revoke(previousContext.refreshToken());
        }
    }

    private UUID authenticatedUserId(Authentication authentication) {
        try {
            return UUID.fromString(authentication.getName());
        } catch (IllegalArgumentException e) {
            throw new InvalidTokenException("Invalid authentication token");
        }
    }

    private Map<String, Object> buildContextResponse(
            String accessToken, String refreshToken, UUID veterinaryId, VeterinaryMembership membership) {
        Map<String, Object> veterinaryInfo = new LinkedHashMap<>();
        veterinaryInfo.put("id", veterinaryId);
        veterinaryInfo.put("role", membership.role());
        veterinaryInfo.put("status", membership.veterinaryStatus());

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("token", accessToken);
        response.put("refreshToken", refreshToken);
        response.put("expiresIn", jwtUtil.getAccessTokenTtlSeconds());
        response.put("veterinary", veterinaryInfo);
        return response;
    }

    private Map<String, Object> buildLoginResponse(User user) {
        String accessToken = buildAccessToken(user);
        String refreshToken = refreshTokenService.issue(user.getId());
        publishLoginEvent(user);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("token", accessToken);
        response.put("refreshToken", refreshToken);
        response.put("expiresIn", jwtUtil.getAccessTokenTtlSeconds());
        return response;
    }

    private Map<String, Object> baseClaims(User user) {
        String email = user.getEmail().trim().toLowerCase(Locale.ROOT);
        String userId = user.getId().toString();

        // Se conservan id/email/username/provider con los mismos nombres y valores que ya
        // emitia este servicio, mas los claims del contrato (CONTRATOS_COMPARTIDOS.md §1.2).
        Map<String, Object> claims = new LinkedHashMap<>();
        claims.put("id", userId);
        claims.put("email", email);
        claims.put("username", user.getUsername());
        claims.put("provider", user.getAuthProvider().name());
        claims.put("email_verified", user.isEmailVerified());
        claims.put("platform_role", user.getPlatformRole().name());
        return claims;
    }

    private String buildAccessToken(User user) {
        Map<String, Object> claims = baseClaims(user);
        claims.put("ctx", "USER");
        return jwtUtil.generateToken(user.getId().toString(), claims);
    }

    private String buildVeterinaryAccessToken(User user, UUID veterinaryId, VeterinaryMembership membership) {
        // Token de contexto de clinica (CONTRATOS_COMPARTIDOS.md §1.3): mantiene TODOS los
        // claims del token base y agrega vet_id/vet_role/employee_id/vet_licensed/vet_status.
        Map<String, Object> claims = baseClaims(user);
        claims.put("ctx", "VETERINARY");
        claims.put("vet_id", veterinaryId.toString());
        claims.put("vet_role", membership.role());
        claims.put("employee_id", membership.employeeId() != null ? membership.employeeId().toString() : null);
        claims.put("vet_licensed", membership.isLicensed());
        claims.put("vet_status", membership.veterinaryStatus());
        return jwtUtil.generateToken(user.getId().toString(), claims);
    }

    private void publishLoginEvent(User user) {
        String email = user.getEmail().trim().toLowerCase(Locale.ROOT);
        publishAuthEventUseCase.publish(AuthEvent.builder()
                .userId(user.getId())
                .email(email)
                .eventType("USER_LOGIN")
                .occurredAt(Instant.now())
                .build());
        log.info("JWT token generated for user: {}", email);
    }
}

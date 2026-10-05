package user.microservice.pets.infrastructure.controllers;

import io.jsonwebtoken.Claims;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import user.microservice.pets.application.dto.AuthEvent;
import user.microservice.pets.application.dto.GoogleTokenRequest;
import user.microservice.pets.application.dto.LoginRequest;
import user.microservice.pets.application.dto.LogoutRequest;
import user.microservice.pets.application.dto.RefreshTokenRequest;
import user.microservice.pets.application.services.LogoutService;
import user.microservice.pets.application.services.RefreshTokenService;
import user.microservice.pets.domain.exceptions.InvalidTokenException;
import user.microservice.pets.domain.model.User;
import user.microservice.pets.domain.ports.in.GoogleAuthUseCase;
import user.microservice.pets.domain.ports.in.LocalAuthUseCase;
import user.microservice.pets.domain.ports.in.PublishAuthEventUseCase;
import user.microservice.pets.domain.ports.out.UserRepositoryPort;
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

    private final GoogleAuthUseCase googleAuthUseCase;
    private final JwtUtil jwtUtil;
    private final LocalAuthUseCase localAuthUseCase;
    private final LogoutService logoutService;
    private final PublishAuthEventUseCase publishAuthEventUseCase;
    private final RefreshTokenService refreshTokenService;
    private final UserRepositoryPort userRepositoryPort;

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
        RefreshTokenService.RotatedRefreshToken rotated = refreshTokenService.rotate(request.refreshToken());

        User user = userRepositoryPort.findById(rotated.userId())
                .orElseThrow(() -> new InvalidTokenException("User not found"));

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("token", buildAccessToken(user));
        response.put("refreshToken", rotated.rawToken());
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

    private String buildAccessToken(User user) {
        String email = user.getEmail().trim().toLowerCase(Locale.ROOT);
        String userId = user.getId().toString();

        // sub = UUID del usuario (contrato compartido). Se conservan id/email/username/provider
        // con los mismos nombres y valores que ya emitia este servicio, y se agregan los claims
        // del contrato (CONTRATOS_COMPARTIDOS.md §1.2): email_verified, platform_role, ctx.
        return jwtUtil.generateToken(userId, Map.of(
                "id", userId,
                "email", email,
                "username", user.getUsername(),
                "provider", user.getAuthProvider().name(),
                "email_verified", user.isEmailVerified(),
                "platform_role", user.getPlatformRole().name(),
                "ctx", "USER"
        ));
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

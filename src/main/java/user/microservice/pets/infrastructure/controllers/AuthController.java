package user.microservice.pets.infrastructure.controllers;

import io.jsonwebtoken.Claims;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import user.microservice.pets.application.dto.AuthEvent;
import user.microservice.pets.application.dto.GoogleTokenRequest;
import user.microservice.pets.application.dto.LoginRequest;
import user.microservice.pets.application.services.LogoutService;
import user.microservice.pets.domain.exceptions.InvalidTokenException;
import user.microservice.pets.domain.model.User;
import user.microservice.pets.domain.ports.in.GoogleAuthUseCase;
import user.microservice.pets.domain.ports.in.LocalAuthUseCase;
import user.microservice.pets.domain.ports.in.PublishAuthEventUseCase;
import user.microservice.pets.infrastructure.security.JwtUtil;

import java.time.Instant;
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

    @CrossOrigin(origins = "http://localhost:8100")
    @PostMapping("/google")
    public ResponseEntity<Map<String, String>> loginWithGoogle(
            @RequestBody GoogleTokenRequest request) {

        String cleanToken = request.idToken().trim();

        User user = googleAuthUseCase.authenticate(cleanToken);

        String jwt = jwtUtil.generateToken(user.getEmail(), Map.of(
                "id", user.getId().toString(),
                "username", user.getUsername(),
                "provider", user.getAuthProvider().name()
        ));

        publishAuthEventUseCase.publish(AuthEvent.builder()
                .userId(user.getId())
                .email(user.getEmail())
                .eventType("USER_LOGIN")
                .occurredAt(Instant.now())
                .build());

        return ResponseEntity.ok(Map.of("token", jwt));
    }

    @PostMapping("/local")
    public ResponseEntity<Map<String, String>> loginLocal(@Valid @RequestBody LoginRequest request) {
        User user = localAuthUseCase.login(request.getEmail(), request.getPassword());

        String jwt = jwtUtil.generateToken(user.getEmail(), Map.of(
                "id", user.getId().toString(),
                "username", user.getUsername(),
                "provider", user.getAuthProvider().name()
        ));

        publishAuthEventUseCase.publish(AuthEvent.builder()
                .userId(user.getId())
                .email(user.getEmail())
                .eventType("USER_LOGIN")
                .occurredAt(Instant.now())
                .build());

        log.info("JWT token generated for user: {}", user.getEmail());
        return ResponseEntity.ok(Map.of("token", jwt));
    }

    @PostMapping("/logout")
    public ResponseEntity<Map<String, String>> logout(
            @RequestHeader(value = "Authorization", required = false) String authHeader) {

        if (authHeader == null || authHeader.trim().isEmpty()) {
            log.warn("Logout attempt without Authorization header");
            throw new InvalidTokenException("Authorization header is required");
        }

        if (!authHeader.startsWith("Bearer ")) {
            log.warn("Logout attempt with invalid Authorization header format");
            throw new InvalidTokenException("Invalid Authorization header format. Must start with 'Bearer '");
        }

        String token = authHeader.substring(7).trim();

        if (token.isEmpty()) {
            log.warn("Logout attempt with empty token");
            throw new InvalidTokenException("Token cannot be empty");
        }

        Claims claims = jwtUtil.validateToken(token);

        String email = claims.getSubject();
        UUID userId = UUID.fromString(claims.get("id", String.class));

        logoutService.logout(token);

        publishAuthEventUseCase.publish(AuthEvent.builder()
                .userId(userId)
                .email(email)
                .eventType("USER_LOGOUT")
                .occurredAt(Instant.now())
                .build());

        log.info("User logged out: {}", email);

        return ResponseEntity.ok(Map.of("message", "Logout successful"));
    }

    @GetMapping("/blacklist/size")
    public ResponseEntity<Map<String, Integer>> getBlacklistSize() {
        return ResponseEntity.ok(Map.of("size", logoutService.getBlacklistSize()));
    }
}
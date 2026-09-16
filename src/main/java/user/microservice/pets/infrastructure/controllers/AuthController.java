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

    @PostMapping("/google")
    public ResponseEntity<Map<String, String>> loginWithGoogle(@RequestBody GoogleTokenRequest request) {
        if (request.idToken() == null || request.idToken().isBlank()) {
            throw new InvalidTokenException("Google idToken is required");
        }
        User user = googleAuthUseCase.authenticate(request.idToken().trim());
        return ResponseEntity.ok(Map.of("token", issueToken(user)));
    }

    @PostMapping("/local")
    public ResponseEntity<Map<String, String>> loginLocal(@Valid @RequestBody LoginRequest request) {
        User user = localAuthUseCase.login(request.getEmail(), request.getPassword());
        return ResponseEntity.ok(Map.of("token", issueToken(user)));
    }

    @PostMapping("/logout")
    public ResponseEntity<Map<String, String>> logout(
            @RequestHeader(value = "Authorization", required = false) String authHeader) {

        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            throw new InvalidTokenException("Authorization header must be 'Bearer <token>'");
        }

        String token = authHeader.substring(7).trim();
        Claims claims = logoutService.logout(token);

        String email = claims.getSubject();
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

    private String issueToken(User user) {
        String email = user.getEmail().trim().toLowerCase(Locale.ROOT);

        String jwt = jwtUtil.generateToken(email, Map.of(
                "id", user.getId().toString(),
                "email", email,
                "username", user.getUsername(),
                "provider", user.getAuthProvider().name()
        ));

        publishAuthEventUseCase.publish(AuthEvent.builder()
                .userId(user.getId())
                .email(email)
                .eventType("USER_LOGIN")
                .occurredAt(Instant.now())
                .build());

        log.info("JWT token generated for user: {}", email);
        return jwt;
    }
}
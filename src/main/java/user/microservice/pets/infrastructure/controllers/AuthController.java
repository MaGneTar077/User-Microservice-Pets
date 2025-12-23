package user.microservice.pets.infrastructure.controllers;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import user.microservice.pets.application.dto.LoginRequest;
import user.microservice.pets.application.services.LogoutService;
import user.microservice.pets.domain.model.User;
import user.microservice.pets.domain.ports.in.GoogleAuthUseCase;
import user.microservice.pets.domain.ports.in.LocalAuthUseCase;
import user.microservice.pets.infrastructure.security.JwtUtil;

import java.util.Map;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final GoogleAuthUseCase googleAuthUseCase;
    private final JwtUtil jwtUtil;
    private final LocalAuthUseCase localAuthUseCase;
    private final LogoutService logoutService;


    @PostMapping("/google")
    public ResponseEntity<Map<String, String>> loginWithGoogle(@RequestBody String idToken) {
        User user = googleAuthUseCase.authenticate(idToken);

        String jwt = jwtUtil.generateToken(user.getEmail(), Map.of(
                "username", user.getUsername(),
                "provider", user.getAuthProvider().name()
        ));

        return ResponseEntity.ok(Map.of("token", jwt));
    }

    @PostMapping("/local")
    public ResponseEntity<Map<String, String>> loginLocal(@RequestBody LoginRequest request) {
        User user = localAuthUseCase.login(request.getEmail(), request.getPassword());

        String jwt = jwtUtil.generateToken(user.getEmail(), Map.of(
                "username", user.getUsername(),
                "provider", user.getAuthProvider().name()
        ));

        return ResponseEntity.ok(Map.of("token", jwt));
    }

    @PostMapping("/logout")
    public ResponseEntity<String> logout(@RequestHeader(value= "Authorization", required = false) String authHeader){
        if (authHeader!= null && authHeader.startsWith("Bearer ")){
            String token= authHeader.substring(7);
            logoutService.logout(token);
        }
        return ResponseEntity.ok("Logout successfully");
    }
}

package user.microservice.pets.infraestructure.controllers;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import user.microservice.pets.domain.model.User;
import user.microservice.pets.domain.ports.in.GoogleAuthUseCase;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final GoogleAuthUseCase googleAuthUseCase;

    @PostMapping("/google")
    public ResponseEntity<User> loginWithGoogle(@RequestBody String idToken) {
        User user = googleAuthUseCase.authenticate(idToken);
        return ResponseEntity.ok(user);
    }
}

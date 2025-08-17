package user.microservice.pets.infrastructure.controllers;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import user.microservice.pets.domain.ports.in.RequestPasswordResetUseCase;
import user.microservice.pets.domain.ports.in.ResetPasswordUseCase;

@RestController
@RequestMapping("/auth")
public class PasswordResetController {

    private final RequestPasswordResetUseCase requestPasswordResetUseCase;
    private final ResetPasswordUseCase resetPasswordUseCase;

    public PasswordResetController(RequestPasswordResetUseCase requestPasswordResetUseCase,
                                   ResetPasswordUseCase resetPasswordUseCase) {
        this.requestPasswordResetUseCase = requestPasswordResetUseCase;
        this.resetPasswordUseCase = resetPasswordUseCase;
    }

    @PostMapping("/request-password-reset")
    public ResponseEntity<Void> requestPasswordReset(@RequestParam String email) {
        requestPasswordResetUseCase.execute(email);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/reset-password")
    public ResponseEntity<Void> resetPassword(@RequestParam String token, @RequestParam String newPassword) {
        resetPasswordUseCase.execute(token, newPassword);
        return ResponseEntity.ok().build();
    }
}

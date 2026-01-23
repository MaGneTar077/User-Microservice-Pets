package user.microservice.pets.infrastructure.controllers;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import user.microservice.pets.application.dto.PasswordResetResponse;
import user.microservice.pets.application.dto.RequestPasswordResetRequest;
import user.microservice.pets.application.dto.ResetPasswordRequest;
import user.microservice.pets.domain.ports.in.RequestPasswordResetUseCase;
import user.microservice.pets.domain.ports.in.ResetPasswordUseCase;

@Slf4j
@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class PasswordResetController {

    private final RequestPasswordResetUseCase requestPasswordResetUseCase;
    private final ResetPasswordUseCase resetPasswordUseCase;

    @PostMapping("/request-password-reset")
    public ResponseEntity<PasswordResetResponse> requestPasswordReset(
            @Valid @RequestBody RequestPasswordResetRequest request) {

        log.info("Password reset requested for email: {}", request.getEmail());
        requestPasswordResetUseCase.execute(request.getEmail());

        return ResponseEntity.ok(new PasswordResetResponse(
                "If the email exists, a password reset link has been sent"
        ));
    }

    @PostMapping("/reset-password")
    public ResponseEntity<PasswordResetResponse> resetPassword(
            @Valid @RequestBody ResetPasswordRequest request) {

        log.info("Password reset attempted with token");
        resetPasswordUseCase.execute(request.getToken(), request.getNewPassword());

        return ResponseEntity.ok(new PasswordResetResponse(
                "Password has been reset successfully"
        ));
    }
}
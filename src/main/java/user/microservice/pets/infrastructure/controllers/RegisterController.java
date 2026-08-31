package user.microservice.pets.infrastructure.controllers;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import user.microservice.pets.application.dto.AuthEvent;
import user.microservice.pets.application.dto.RegisterRequest;
import user.microservice.pets.application.dto.RegisterResponse;
import user.microservice.pets.application.services.RegisterService;
import user.microservice.pets.domain.ports.in.PublishAuthEventUseCase;

import java.time.Instant;

@RestController
@RequestMapping("/user")
@RequiredArgsConstructor
public class RegisterController {

    private final RegisterService registerService;
    private final PublishAuthEventUseCase publishAuthEventUseCase;

    @PostMapping("/register")
    public ResponseEntity<RegisterResponse> register(@Valid @RequestBody RegisterRequest request) {
        RegisterResponse response = registerService.register(request);

        publishAuthEventUseCase.publish(AuthEvent.builder()
                .userId(response.getUuid())
                .email(response.getEmail())
                .eventType("USER_REGISTERED")
                .occurredAt(Instant.now())
                .build());

        return new ResponseEntity<>(response, HttpStatus.CREATED);
    }
}
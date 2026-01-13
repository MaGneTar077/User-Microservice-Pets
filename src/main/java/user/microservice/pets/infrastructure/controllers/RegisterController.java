package user.microservice.pets.infrastructure.controllers;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import user.microservice.pets.application.dto.RegisterRequest;
import user.microservice.pets.application.dto.RegisterResponse;
import user.microservice.pets.application.services.RegisterService;

@RestController
@RequestMapping("/user")
@RequiredArgsConstructor
public class RegisterController {

    private final RegisterService registerService;

    @PostMapping("/register")
    public ResponseEntity<RegisterResponse> register(@Valid @RequestBody RegisterRequest request) {
        RegisterResponse response = registerService.register(request);
        return new ResponseEntity<>(response, HttpStatus.CREATED);
    }
}
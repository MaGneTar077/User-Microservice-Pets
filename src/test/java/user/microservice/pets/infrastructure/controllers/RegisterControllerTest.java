package user.microservice.pets.infrastructure.controllers;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import user.microservice.pets.application.dto.RegisterRequest;
import user.microservice.pets.application.dto.RegisterResponse;
import user.microservice.pets.application.services.RegisterService;
import user.microservice.pets.domain.ports.in.PublishAuthEventUseCase;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class RegisterControllerTest {

    private RegisterService registerService;
    private PublishAuthEventUseCase publishAuthEventUseCase;
    private RegisterController registerController;

    @BeforeEach
    void setup() {
        registerService = mock(RegisterService.class);
        publishAuthEventUseCase = mock(PublishAuthEventUseCase.class);
        registerController = new RegisterController(registerService, publishAuthEventUseCase);
    }

    @Test
    void shouldReturn201WhenUserIsRegistered() {
        RegisterRequest request = new RegisterRequest();
        request.setUsername("Gus");
        request.setEmail("gus@example.com");
        request.setPassword("secreta123");

        RegisterResponse response = new RegisterResponse(
                UUID.randomUUID(),
                "Gus",
                "gus@example.com",
                LocalDateTime.now()
        );

        when(registerService.register(request)).thenReturn(response);

        ResponseEntity<?> result = registerController.register(request);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(result.getBody()).isInstanceOf(RegisterResponse.class);
        assertThat(((RegisterResponse) result.getBody()).getEmail()).isEqualTo("gus@example.com");
    }

}
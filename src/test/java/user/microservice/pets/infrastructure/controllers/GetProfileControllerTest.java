package user.microservice.pets.infrastructure.controllers;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.ResponseEntity;
import user.microservice.pets.application.dto.ProfileResponse;
import user.microservice.pets.domain.enums.AuthProvider;
import user.microservice.pets.domain.model.User;
import user.microservice.pets.domain.ports.in.GetProfileUseCase;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.when;

public class GetProfileControllerTest {

    @Test
    void shouldReturnProfileResponseWhenUserExists() {
        UUID userId = UUID.randomUUID();
        User user = new User(userId, "luis", "luis@test.com", "pass123", LocalDateTime.now(), AuthProvider.LOCAL);

        GetProfileUseCase getProfileUseCase = Mockito.mock(GetProfileUseCase.class);
        when(getProfileUseCase.getProfile(userId)).thenReturn(user);

        GetProfileController controller = new GetProfileController(getProfileUseCase);

        ResponseEntity<ProfileResponse> response = controller.getProfile(userId);

        assertEquals(200, response.getStatusCodeValue());
        assertNotNull(response.getBody());
        assertEquals("luis", response.getBody().getUsername());
        assertEquals("luis@test.com", response.getBody().getEmail());
    }
}

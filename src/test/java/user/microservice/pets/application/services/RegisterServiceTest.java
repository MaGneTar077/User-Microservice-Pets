package user.microservice.pets.application.services;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import user.microservice.pets.application.dto.RegisterRequest;
import user.microservice.pets.application.dto.RegisterResponse;
import user.microservice.pets.domain.enums.AuthProvider;
import user.microservice.pets.domain.model.User;
import user.microservice.pets.domain.ports.in.RegisterUserUseCase;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RegisterServiceTest {

    @Mock
    private RegisterUserUseCase registerUserUseCase;

    @InjectMocks
    private RegisterService registerService;

    private RegisterRequest request;
    private User savedUser;

    @BeforeEach
    void setUp() {
        request = new RegisterRequest();
        request.setUsername("Gus");
        request.setEmail("gus@example.com");
        request.setPassword("secreta123");

        savedUser = new User();
        savedUser.setId(UUID.randomUUID());
        savedUser.setUsername("Gus");
        savedUser.setEmail("gus@example.com");
        savedUser.setPassword("secreta123");
        savedUser.setAuthProvider(AuthProvider.LOCAL);
        savedUser.setCreatedAt(LocalDateTime.now());
    }

    @Test
    void shouldRegisterUserSuccessfully() {
        when(registerUserUseCase.register(any(User.class))).thenReturn(savedUser);

        RegisterResponse response = registerService.register(request);

        assertNotNull(response);
        assertEquals(savedUser.getId(), response.getUuid());
        assertEquals(savedUser.getUsername(), response.getUsername());
        assertEquals(savedUser.getEmail(), response.getEmail());
        assertEquals(savedUser.getCreatedAt(), response.getCreatedAt());

        verify(registerUserUseCase).register(any(User.class));
    }

    @Test
    void shouldThrowExceptionWhenEmailExists() {
        when(registerUserUseCase.register(any(User.class)))
                .thenThrow(new IllegalArgumentException("Email already exists"));

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> registerService.register(request));

        assertEquals("Email already exists", exception.getMessage());
    }

}

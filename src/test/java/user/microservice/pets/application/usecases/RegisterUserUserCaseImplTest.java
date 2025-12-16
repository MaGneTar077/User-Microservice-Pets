package user.microservice.pets.application.usecases;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.springframework.security.crypto.password.PasswordEncoder;
import user.microservice.pets.domain.model.User;
import user.microservice.pets.domain.ports.out.UserRepositoryPort;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RegisterUserUseCaseImplTest {

    @Mock
    private UserRepositoryPort userRepositoryPort;

    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private RegisterUserUseCaseImpl registerUserUseCase;

    private User user;

    @BeforeEach
    void setUp() {
        user = new User();
        user.setEmail("correo@example.com");
        user.setPassword("secreta123");
    }

    @Test
    void shouldRegisterUserWhenEmailNotExists() {
        // Arrange
        when(userRepositoryPort.existsByEmail(user.getEmail())).thenReturn(false);
        when(passwordEncoder.encode(user.getPassword())).thenReturn("encodedPassword");
        when(userRepositoryPort.save(any(User.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        User result = registerUserUseCase.register(user);

        // Assert
        assertNotNull(result.getId(), "El ID debe haberse generado");
        assertNotNull(result.getCreatedAt(), "La fecha de creación debe haberse establecido");
        assertEquals("encodedPassword", result.getPassword());
        verify(userRepositoryPort).save(any(User.class));
    }

    @Test
    void shouldThrowExceptionWhenEmailAlreadyExists() {
        // Arrange
        when(userRepositoryPort.existsByEmail(user.getEmail())).thenReturn(true);

        // Act & Assert
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            registerUserUseCase.register(user);
        });

        assertEquals("Email already exists", exception.getMessage(),
                "Debe lanzar un error  si el email ya existe");
        verify(userRepositoryPort, never()).save(any(User.class));
    }
}

package user.microservice.pets.application.usecases;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.springframework.security.crypto.password.PasswordEncoder;
import user.microservice.pets.application.services.EmailVerificationService;
import user.microservice.pets.domain.exceptions.InvalidPhoneException;
import user.microservice.pets.domain.exceptions.InvalidUserDataException;
import user.microservice.pets.domain.exceptions.UserAlreadyExistsException;
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

    @Mock
    private EmailVerificationService emailVerificationService;

    @InjectMocks
    private RegisterUserUseCaseImpl registerUserUseCase;

    private User user;

    @BeforeEach
    void setUp() {
        user = new User();
        user.setUsername("testuser");
        user.setEmail("correo@example.com");
        user.setPassword("Secreta123!");
    }

    @Test
    void shouldRegisterUserWhenEmailNotExists() {
        // Arrange
        when(userRepositoryPort.existsByEmail(user.getEmail())).thenReturn(false);
        when(userRepositoryPort.existsByUsername(user.getUsername())).thenReturn(false);
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
        verify(emailVerificationService).sendCode(result);
    }

    @Test
    void shouldThrowExceptionWhenEmailAlreadyExists() {
        // Arrange
        when(userRepositoryPort.existsByEmail(user.getEmail())).thenReturn(true);

        // Act & Assert
        UserAlreadyExistsException exception = assertThrows(UserAlreadyExistsException.class, () -> {
            registerUserUseCase.register(user);
        });

        assertEquals("Email already exists", exception.getMessage(),
                "Debe lanzar un error  si el email ya existe");
        verify(userRepositoryPort, never()).save(any(User.class));
    }

    private void stubHappyPath() {
        when(userRepositoryPort.existsByEmail(anyString())).thenReturn(false);
        when(userRepositoryPort.existsByUsername(anyString())).thenReturn(false);
        when(passwordEncoder.encode(anyString())).thenReturn("encodedPassword");
        when(userRepositoryPort.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void shouldRegisterWithFullNameContainingSpaces() {
        user.setFullName("Luis Santiago");
        stubHappyPath();

        User result = registerUserUseCase.register(user);

        assertEquals("Luis Santiago", result.getFullName());
        // El username sigue sin admitir espacios, independiente de fullName.
        assertEquals("testuser", result.getUsername());
    }

    @Test
    void shouldRejectFullNameWithAngleBrackets() {
        user.setFullName("Luis <script>");
        assertThrows(InvalidUserDataException.class, () -> registerUserUseCase.register(user));
        verify(userRepositoryPort, never()).save(any(User.class));
    }

    @Test
    void shouldRejectFullNameShorterThanTwoCharacters() {
        user.setFullName("L");
        assertThrows(InvalidUserDataException.class, () -> registerUserUseCase.register(user));
    }

    @Test
    void shouldRejectFullNameLongerThan100Characters() {
        user.setFullName("A".repeat(101));
        assertThrows(InvalidUserDataException.class, () -> registerUserUseCase.register(user));
    }

    @Test
    void shouldNormalizeLocalPhoneToE164() {
        user.setPhone("3001234567");
        stubHappyPath();

        User result = registerUserUseCase.register(user);

        assertEquals("+573001234567", result.getPhone());
    }

    @Test
    void shouldNormalizePhoneWithSpacesAndCountryCodeToE164() {
        user.setPhone("+57 300 123 4567");
        stubHappyPath();

        User result = registerUserUseCase.register(user);

        assertEquals("+573001234567", result.getPhone());
    }

    @Test
    void shouldAcceptAValidInternationalPhone() {
        user.setPhone("+14155552671");
        stubHappyPath();

        User result = registerUserUseCase.register(user);

        assertEquals("+14155552671", result.getPhone());
    }

    @Test
    void shouldRejectInvalidPhoneText() {
        user.setPhone("not-a-phone");
        assertThrows(InvalidPhoneException.class, () -> registerUserUseCase.register(user));
        verify(userRepositoryPort, never()).save(any(User.class));
    }

    @Test
    void shouldRegisterWithoutPhone() {
        user.setPhone(null);
        stubHappyPath();

        User result = registerUserUseCase.register(user);

        assertNull(result.getPhone());
    }

    @Test
    void shouldAllowTwoUsersWithSameFullNameAndPhone() {
        user.setFullName("Luis Santiago");
        user.setPhone("3001234567");
        stubHappyPath();
        User first = registerUserUseCase.register(user);

        User other = new User();
        other.setUsername("otheruser");
        other.setEmail("other@example.com");
        other.setPassword("Secreta123!");
        other.setFullName("Luis Santiago");
        other.setPhone("3001234567");
        User second = registerUserUseCase.register(other);

        assertEquals(first.getFullName(), second.getFullName());
        assertEquals(first.getPhone(), second.getPhone());
    }
}

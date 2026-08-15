package user.microservice.pets.application.usecases;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import user.microservice.pets.domain.model.User;
import user.microservice.pets.domain.ports.out.UserRepositoryPort;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

public class GetProfileUseCaseImplTest {

    private UserRepositoryPort userRepositoryPort;
    private GetProfileUseCaseImpl getProfileUseCase;

    @BeforeEach
    void setUp() {
        userRepositoryPort = Mockito.mock(UserRepositoryPort.class);
        getProfileUseCase = new GetProfileUseCaseImpl(userRepositoryPort);
    }

    @Test
    void ShouldReturnUserWhenExists() {
        UUID userId = UUID.randomUUID();
        User user = new User(userId, "luis", "luis@test.com", "pass123", LocalDateTime.now(), null);

        when(userRepositoryPort.findById(userId)).thenReturn(Optional.of(user));

        User result = getProfileUseCase.getProfile(userId);

        assertNotNull(result);
        assertEquals("luis", result.getUsername());
        verify(userRepositoryPort, times(1)).findById(userId);
    }

    @Test
    void shouldThrowExceptionWhenUserNotFound() {
        UUID userId = UUID.randomUUID();

        when(userRepositoryPort.findById(userId)).thenReturn(Optional.empty());

        RuntimeException exception = assertThrows(RuntimeException.class, () -> getProfileUseCase.getProfile(userId));

        assertEquals("User not found with id: " + userId, exception.getMessage());
        verify(userRepositoryPort, times(1)).findById(userId);
    }

}

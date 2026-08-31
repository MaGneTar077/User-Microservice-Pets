package user.microservice.pets.application.usecases;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import user.microservice.pets.domain.exceptions.UserNotFoundException;
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
        User user = User.builder()
                .id(userId)
                .username("luis")
                .email("luis@test.com")
                .password("pass123")
                .createdAt(LocalDateTime.now())
                .authProvider(null)
                .build();

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

        UserNotFoundException exception = assertThrows(UserNotFoundException.class, () -> getProfileUseCase.getProfile(userId));

        assertEquals("User not found", exception.getMessage());
        verify(userRepositoryPort, times(1)).findById(userId);
    }

}
package user.microservice.pets.application.usecases;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import user.microservice.pets.domain.enums.AuthProvider;
import user.microservice.pets.domain.model.User;
import user.microservice.pets.domain.ports.out.UserRepositoryPort;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("UpdateUserProfileUseCase - Unit Tests")
class UpdateUserProfileUseCaseImplTest {

    @Mock
    private UserRepositoryPort userRepositoryPort;

    @InjectMocks
    private UpdateUserProfileUseCaseImpl updateUserProfileUseCase;

    private User existingUser;
    private UUID userId;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        existingUser = User.builder()
                .id(userId)
                .username("oldUsername")
                .email("user@example.com")
                .password("encodedPassword")
                .createdAt(LocalDateTime.now())
                .authProvider(AuthProvider.GOOGLE)
                .build();
    }

    @Test
    @DisplayName("Should update username successfully")
    void shouldUpdateUsernameSuccessfully() {
        // Given
        String newUsername = "newUsername";
        User userToUpdate = User.builder()
                .id(userId)
                .username(newUsername)
                .build();

        User updatedUser = User.builder()
                .id(userId)
                .username(newUsername)
                .email(existingUser.getEmail())
                .password(existingUser.getPassword())
                .createdAt(existingUser.getCreatedAt())
                .authProvider(existingUser.getAuthProvider())
                .build();

        when(userRepositoryPort.findById(userId)).thenReturn(Optional.of(existingUser));
        when(userRepositoryPort.existsByUsername(newUsername)).thenReturn(false);
        when(userRepositoryPort.save(any(User.class))).thenReturn(updatedUser);

        // When
        User result = updateUserProfileUseCase.updateProfile(userToUpdate);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getUsername()).isEqualTo(newUsername);
        assertThat(result.getEmail()).isEqualTo(existingUser.getEmail());

        verify(userRepositoryPort, times(1)).findById(userId);
        verify(userRepositoryPort, times(1)).existsByUsername(newUsername);
        verify(userRepositoryPort, times(1)).save(any(User.class));
    }

    @Test
    @DisplayName("Should keep same username if not changed")
    void shouldKeepSameUsernameIfNotChanged() {
        // Given
        String sameUsername = "oldUsername";
        User userToUpdate = User.builder()
                .id(userId)
                .username(sameUsername)
                .build();

        when(userRepositoryPort.findById(userId)).thenReturn(Optional.of(existingUser));
        when(userRepositoryPort.existsByUsername(sameUsername)).thenReturn(true);
        when(userRepositoryPort.save(any(User.class))).thenReturn(existingUser);

        // When
        User result = updateUserProfileUseCase.updateProfile(userToUpdate);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getUsername()).isEqualTo(sameUsername);

        verify(userRepositoryPort, times(1)).findById(userId);
        verify(userRepositoryPort, times(1)).existsByUsername(sameUsername);
        verify(userRepositoryPort, times(1)).save(any(User.class));
    }

    @Test
    @DisplayName("Should throw RuntimeException when user does not exist")
    void shouldThrowRuntimeExceptionWhenUserDoesNotExist() {
        // Given
        UUID nonExistentId = UUID.randomUUID();
        User userToUpdate = User.builder()
                .id(nonExistentId)
                .username("newUsername")
                .build();

        when(userRepositoryPort.findById(nonExistentId)).thenReturn(Optional.empty());

        // When & Then
        assertThatThrownBy(() -> updateUserProfileUseCase.updateProfile(userToUpdate))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("User not found with id: " + nonExistentId);

        verify(userRepositoryPort, times(1)).findById(nonExistentId);
        verify(userRepositoryPort, never()).existsByUsername(anyString());
        verify(userRepositoryPort, never()).save(any(User.class));
    }

    @Test
    @DisplayName("Should throw RuntimeException when username already exists")
    void shouldThrowRuntimeExceptionWhenUsernameAlreadyExists() {
        // Given
        String takenUsername = "takenUsername";
        User userToUpdate = User.builder()
                .id(userId)
                .username(takenUsername)
                .build();

        when(userRepositoryPort.findById(userId)).thenReturn(Optional.of(existingUser));
        when(userRepositoryPort.existsByUsername(takenUsername)).thenReturn(true);

        // When & Then
        assertThatThrownBy(() -> updateUserProfileUseCase.updateProfile(userToUpdate))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Username already exists");

        verify(userRepositoryPort, times(1)).findById(userId);
        verify(userRepositoryPort, times(1)).existsByUsername(takenUsername);
        verify(userRepositoryPort, never()).save(any(User.class));
    }

    @Test
    @DisplayName("Should call repository methods in correct order")
    void shouldCallRepositoryMethodsInCorrectOrder() {
        // Given
        String newUsername = "newUsername";
        User userToUpdate = User.builder()
                .id(userId)
                .username(newUsername)
                .build();

        when(userRepositoryPort.findById(userId)).thenReturn(Optional.of(existingUser));
        when(userRepositoryPort.existsByUsername(newUsername)).thenReturn(false);
        when(userRepositoryPort.save(any(User.class))).thenReturn(existingUser);

        // When
        updateUserProfileUseCase.updateProfile(userToUpdate);

        // Then - verify order of calls
        var inOrder = inOrder(userRepositoryPort);
        inOrder.verify(userRepositoryPort).findById(userId);
        inOrder.verify(userRepositoryPort).existsByUsername(newUsername);
        inOrder.verify(userRepositoryPort).save(any(User.class));
    }

    @Test
    @DisplayName("Should preserve email when updating username")
    void shouldPreserveEmailWhenUpdatingUsername() {
        // Given
        String newUsername = "newUsername";
        String originalEmail = "user@example.com";
        User userToUpdate = User.builder()
                .id(userId)
                .username(newUsername)
                .build();

        User updatedUser = User.builder()
                .id(userId)
                .username(newUsername)
                .email(originalEmail)
                .password(existingUser.getPassword())
                .createdAt(existingUser.getCreatedAt())
                .authProvider(existingUser.getAuthProvider())
                .build();

        when(userRepositoryPort.findById(userId)).thenReturn(Optional.of(existingUser));
        when(userRepositoryPort.existsByUsername(newUsername)).thenReturn(false);
        when(userRepositoryPort.save(any(User.class))).thenReturn(updatedUser);

        // When
        User result = updateUserProfileUseCase.updateProfile(userToUpdate);

        // Then
        assertThat(result.getEmail()).isEqualTo(originalEmail);
    }

    @Test
    @DisplayName("Should preserve password when updating username")
    void shouldPreservePasswordWhenUpdatingUsername() {
        // Given
        String newUsername = "newUsername";
        String originalPassword = "encodedPassword";
        User userToUpdate = User.builder()
                .id(userId)
                .username(newUsername)
                .build();

        User updatedUser = User.builder()
                .id(userId)
                .username(newUsername)
                .email(existingUser.getEmail())
                .password(originalPassword)
                .createdAt(existingUser.getCreatedAt())
                .authProvider(existingUser.getAuthProvider())
                .build();

        when(userRepositoryPort.findById(userId)).thenReturn(Optional.of(existingUser));
        when(userRepositoryPort.existsByUsername(newUsername)).thenReturn(false);
        when(userRepositoryPort.save(any(User.class))).thenReturn(updatedUser);

        // When
        User result = updateUserProfileUseCase.updateProfile(userToUpdate);

        // Then
        assertThat(result.getPassword()).isEqualTo(originalPassword);
    }

    @Test
    @DisplayName("Should preserve authProvider when updating username")
    void shouldPreserveAuthProviderWhenUpdatingUsername() {
        // Given
        String newUsername = "newUsername";
        AuthProvider originalProvider = AuthProvider.GOOGLE;
        User userToUpdate = User.builder()
                .id(userId)
                .username(newUsername)
                .build();

        User updatedUser = User.builder()
                .id(userId)
                .username(newUsername)
                .email(existingUser.getEmail())
                .password(existingUser.getPassword())
                .createdAt(existingUser.getCreatedAt())
                .authProvider(originalProvider)
                .build();

        when(userRepositoryPort.findById(userId)).thenReturn(Optional.of(existingUser));
        when(userRepositoryPort.existsByUsername(newUsername)).thenReturn(false);
        when(userRepositoryPort.save(any(User.class))).thenReturn(updatedUser);

        // When
        User result = updateUserProfileUseCase.updateProfile(userToUpdate);

        // Then
        assertThat(result.getAuthProvider()).isEqualTo(originalProvider);
    }
}

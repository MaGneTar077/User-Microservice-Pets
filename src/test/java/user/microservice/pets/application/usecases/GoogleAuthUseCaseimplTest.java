package user.microservice.pets.application.usecases;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import user.microservice.pets.domain.enums.AuthProvider;
import user.microservice.pets.domain.model.User;
import user.microservice.pets.domain.ports.in.GoogleAuthUseCase;
import user.microservice.pets.domain.ports.out.UserRepositoryPort;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

public class GoogleAuthUseCaseimplTest {

    private UserRepositoryPort userRepositoryPort;
    private GoogleAuthUseCase googleAuthUseCase;

    @BeforeEach
    void setup () {
        userRepositoryPort = mock(UserRepositoryPort.class);
        googleAuthUseCase = new GoogleAuthUseCaseImpl(userRepositoryPort);
    }

    @Test
    void shouldReturnExistingUserWhenEmailExits() {
        //Given
        String email = "test@gmail.com";
        User existingUser = new User();
        existingUser.setEmail(email);

        when(userRepositoryPort.findByEmail(email)).thenReturn(Optional.of(existingUser));

        //When
        User result = googleAuthUseCase.authenticate(email);

        //Then
        assertThat(result).isEqualTo(existingUser);
        verify(userRepositoryPort, never()).save(any());

    }

    @Test
    void shouldCreateAndSaveNewUserWhenEmailDoesNotExist() {

        //Given
        String email = "newuser@gmail.com";
        when(userRepositoryPort.findByEmail(email)).thenReturn(Optional.empty());

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        when(userRepositoryPort.save(userCaptor.capture())).thenAnswer(invocation -> invocation.getArgument(0));

        //When
        User result = googleAuthUseCase.authenticate(email);

        //Then
        verify(userRepositoryPort).save(any(User.class));
        assertThat(result.getEmail()).isEqualTo(email);
        assertThat(result.getUsername()).isEqualTo("newuser");
        assertThat(result.getAuthProvider()).isEqualTo(AuthProvider.GOOGLE);
        assertThat(result.getId()).isNotNull();
        assertThat(result.getCreatedAt()).isNotNull();
    }

}

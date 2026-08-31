package user.microservice.pets.application.usecases;

import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import user.microservice.pets.application.services.GoogleTokenVerifierService;
import user.microservice.pets.domain.enums.AuthProvider;
import user.microservice.pets.domain.model.User;
import user.microservice.pets.domain.ports.in.GoogleAuthUseCase;
import user.microservice.pets.domain.ports.out.UserRepositoryPort;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

public class GoogleAuthUseCaseImplTest {

    private UserRepositoryPort userRepositoryPort;
    private GoogleTokenVerifierService googleTokenVerifierService;
    private GoogleAuthUseCase googleAuthUseCase;

    @BeforeEach
    void setup() {
        userRepositoryPort = mock(UserRepositoryPort.class);
        googleTokenVerifierService = mock(GoogleTokenVerifierService.class);
        googleAuthUseCase = new GoogleAuthUseCaseImpl(userRepositoryPort, googleTokenVerifierService);
    }

    private GoogleIdToken.Payload buildPayload(String email, String name, String picture) {
        GoogleIdToken.Payload payload = new GoogleIdToken.Payload();
        payload.setEmail(email);
        payload.put("name", name);
        payload.put("picture", picture);
        return payload;
    }

    @Test
    void shouldReturnExistingUserWhenEmailExits() {
        //Given
        String email = "test@gmail.com";
        String fakeIdToken = "fake-id-token";

        User existingUser = new User();
        existingUser.setEmail(email);

        when(googleTokenVerifierService.verify(fakeIdToken))
                .thenReturn(buildPayload(email, "Test User", "http://pic.url"));
        when(userRepositoryPort.findByEmail(email)).thenReturn(Optional.of(existingUser));

        //When
        User result = googleAuthUseCase.authenticate(fakeIdToken);

        //Then
        assertThat(result).isEqualTo(existingUser);
        verify(userRepositoryPort, never()).save(any(User.class));
    }

    @Test
    void shouldCreateAndSaveNewUserWhenEmailDoesNotExist() {
        //Given
        String email = "newuser@gmail.com";
        String fakeIdToken = "fake-id-token";

        when(googleTokenVerifierService.verify(fakeIdToken))
                .thenReturn(buildPayload(email, null, null));
        when(userRepositoryPort.findByEmail(email)).thenReturn(Optional.empty());
        when(userRepositoryPort.existsByUsername(anyString())).thenReturn(false);

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        when(userRepositoryPort.save(userCaptor.capture())).thenAnswer(invocation -> invocation.getArgument(0));

        //When
        User result = googleAuthUseCase.authenticate(fakeIdToken);

        //Then
        verify(userRepositoryPort).save(any(User.class));
        assertThat(result.getEmail()).isEqualTo(email);
        assertThat(result.getUsername()).isEqualTo("newuser");
        assertThat(result.getAuthProvider()).isEqualTo(AuthProvider.GOOGLE);
        assertThat(result.getId()).isNotNull();
        assertThat(result.getCreatedAt()).isNotNull();
    }

}
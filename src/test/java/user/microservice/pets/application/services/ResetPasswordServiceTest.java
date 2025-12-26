package user.microservice.pets.application.services;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;
import user.microservice.pets.domain.model.PasswordResetToken;
import user.microservice.pets.domain.model.User;
import user.microservice.pets.domain.ports.out.EmailSenderPort;
import user.microservice.pets.domain.ports.out.PasswordResetTokenRepositoryPort;
import user.microservice.pets.domain.ports.out.UserRepositoryPort;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class ResetPasswordServiceTest {

    private UserRepositoryPort userRepository;
    private PasswordResetTokenRepositoryPort tokenRepository;
    private EmailSenderPort emailSender;
    private PasswordEncoder passwordEncoder;
    private ResetPasswordService service;

    @BeforeEach
    void setup() {
        userRepository = mock(UserRepositoryPort.class);
        tokenRepository = mock(PasswordResetTokenRepositoryPort.class);
        emailSender = mock(EmailSenderPort.class);
        passwordEncoder = mock(PasswordEncoder.class);

        service = new ResetPasswordService(
                userRepository,
                tokenRepository,
                emailSender,
                passwordEncoder
        );
    }

    @Test
    void shouldFailWhenTokenDoesNotExist() {
        when(tokenRepository.findByToken("bad-token"))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() ->
                service.execute("bad-token", "newPass")
        ).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldFailWhenTokenIsExpired() {
        PasswordResetToken token = new PasswordResetToken(
                "token",
                "test@mail.com",
                LocalDateTime.now().minusMinutes(1)
        );

        when(tokenRepository.findByToken("token"))
                .thenReturn(Optional.of(token));

        assertThatThrownBy(() ->
                service.execute("token", "newPass")
        ).isInstanceOf(IllegalArgumentException.class);

        verify(tokenRepository).deleteByToken("token");
        verify(userRepository, never()).save(any());
        verify(emailSender, never()).sendEmail(any(), any(), any());
    }

    @Test
    void shouldResetPasswordSuccessfully() {
        PasswordResetToken token = new PasswordResetToken(
                "token",
                "test@mail.com",
                LocalDateTime.now().plusMinutes(10)
        );

        User user = User.builder()
                .id(UUID.randomUUID())
                .email("test@mail.com")
                .password("oldPass")
                .build();

        when(tokenRepository.findByToken("token"))
                .thenReturn(Optional.of(token));
        when(userRepository.findByEmail("test@mail.com"))
                .thenReturn(Optional.of(user));
        when(passwordEncoder.encode("newPass"))
                .thenReturn("encodedPass");

        service.execute("token", "newPass");

        verify(passwordEncoder).encode("newPass");
        verify(userRepository).save(user);
        verify(tokenRepository).deleteByToken("token");
        verify(emailSender).sendEmail(
                eq("test@mail.com"),
                eq("Contraseña cambiada con éxito"),
                anyString()
        );
    }
}

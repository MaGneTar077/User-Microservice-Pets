package user.microservice.pets.application.services;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import user.microservice.pets.domain.model.User;
import user.microservice.pets.domain.ports.out.EmailSenderPort;
import user.microservice.pets.domain.ports.out.PasswordResetTokenRepositoryPort;
import user.microservice.pets.domain.ports.out.UserRepositoryPort;

import java.util.Optional;
import java.util.UUID;

import static org.mockito.Mockito.*;

class RequestPasswordResetServiceTest {

    private UserRepositoryPort userRepository;
    private PasswordResetTokenRepositoryPort tokenRepository;
    private EmailSenderPort emailSender;
    private RequestPasswordResetService service;

    @BeforeEach
    void setup() {
        userRepository = mock(UserRepositoryPort.class);
        tokenRepository = mock(PasswordResetTokenRepositoryPort.class);
        emailSender = mock(EmailSenderPort.class);

        service = new RequestPasswordResetService(
                userRepository,
                tokenRepository,
                emailSender
        );
    }

    @Test
    void shouldDoNothingWhenUserDoesNotExist() {
        when(userRepository.findByEmail("test@mail.com"))
                .thenReturn(Optional.empty());

        service.execute("test@mail.com");

        verify(tokenRepository, never()).save(any());
        verify(emailSender, never()).sendEmail(any(), any(), any());
    }

    @Test
    void shouldGenerateTokenAndSendEmailWhenUserExists() {
        User user = User.builder()
                .id(UUID.randomUUID())
                .email("test@mail.com")
                .build();

        when(userRepository.findByEmail("test@mail.com"))
                .thenReturn(Optional.of(user));

        service.execute("test@mail.com");

        verify(tokenRepository).deleteByEmail("test@mail.com");
        verify(tokenRepository).save(any());
        verify(emailSender).sendEmail(
                eq("test@mail.com"),
                any(),
                contains("reset-password")
        );
    }
}

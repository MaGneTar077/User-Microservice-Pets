package user.microservice.pets.infrastructure.email;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

import static org.mockito.Mockito.*;

class EmailServiceAdapterTest {

    private JavaMailSender mailSender;
    private EmailServiceAdapter emailService;

    @BeforeEach
    void setup() {
        mailSender = mock(JavaMailSender.class);
        emailService = new EmailServiceAdapter(mailSender, "noreply@test.com");
    }

    @Test
    void shouldSendEmailSuccessfully() {
        emailService.sendEmail(
                "test@mail.com",
                "Subject",
                "Body content"
        );

        verify(mailSender).send(any(SimpleMailMessage.class));
    }
}

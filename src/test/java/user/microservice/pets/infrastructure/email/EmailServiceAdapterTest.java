package user.microservice.pets.infrastructure.email;

import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mail.javamail.JavaMailSender;

import java.util.Properties;

import static org.mockito.Mockito.*;

class EmailServiceAdapterTest {

    private JavaMailSender mailSender;
    private EmailServiceAdapter emailService;

    @BeforeEach
    void setup() {
        mailSender = mock(JavaMailSender.class);
        when(mailSender.createMimeMessage())
                .thenReturn(new MimeMessage(Session.getDefaultInstance(new Properties())));
        emailService = new EmailServiceAdapter(mailSender, "noreply@test.com", "MyAnimaLog", "");
    }

    @Test
    void shouldSendEmailSuccessfully() {
        emailService.sendEmail(
                "test@mail.com",
                "Subject",
                "Body content"
        );

        verify(mailSender).send(any(MimeMessage.class));
    }
}

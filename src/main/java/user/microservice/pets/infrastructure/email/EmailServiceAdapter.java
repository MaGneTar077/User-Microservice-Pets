package user.microservice.pets.infrastructure.email;

import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;
import user.microservice.pets.domain.ports.out.EmailSenderPort;
import org.springframework.beans.factory.annotation.Value;

@Component
public class EmailServiceAdapter implements EmailSenderPort {

    private final JavaMailSender mailSender;
    private final String fromEmail;

    public EmailServiceAdapter(JavaMailSender mailSender,
                               @Value("${spring.mail.username}") String fromEmail) {
        this.mailSender = mailSender;
        this.fromEmail = fromEmail;
    }

    @Override
    public void sendEmail(String to, String subject, String body) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(fromEmail);
        message.setTo(to);
        message.setSubject(subject);
        message.setText(body);
        mailSender.send(message);
    }
}


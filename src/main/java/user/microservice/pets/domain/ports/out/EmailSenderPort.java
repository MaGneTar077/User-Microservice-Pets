package user.microservice.pets.domain.ports.out;

public interface EmailSenderPort {
    void sendEmail(String to, String subject, String body);
}

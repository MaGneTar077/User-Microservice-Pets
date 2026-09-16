package user.microservice.pets.domain.exceptions;

public class EmailNotVerifiedException extends RuntimeException {
    public EmailNotVerifiedException() {
        super("Email not verified. Check your inbox for the verification code");
    }
}

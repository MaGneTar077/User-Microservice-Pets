package user.microservice.pets.domain.exceptions;

public class InvalidVerificationCodeException extends RuntimeException {

    public InvalidVerificationCodeException() {
        super("Invalid or expired verification code");
    }

    public InvalidVerificationCodeException(String message) {
        super(message);
    }
}

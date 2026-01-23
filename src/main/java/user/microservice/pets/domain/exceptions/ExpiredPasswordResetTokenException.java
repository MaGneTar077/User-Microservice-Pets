package user.microservice.pets.domain.exceptions;

public class ExpiredPasswordResetTokenException extends RuntimeException {
    public ExpiredPasswordResetTokenException(String message) {
        super(message);
    }
}

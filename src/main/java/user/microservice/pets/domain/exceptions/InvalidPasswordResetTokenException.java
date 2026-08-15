package user.microservice.pets.domain.exceptions;

public class InvalidPasswordResetTokenException extends RuntimeException{
    public InvalidPasswordResetTokenException(String message) {
        super(message);
    }
}

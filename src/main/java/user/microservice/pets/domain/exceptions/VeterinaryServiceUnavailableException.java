package user.microservice.pets.domain.exceptions;

public class VeterinaryServiceUnavailableException extends RuntimeException {
    public VeterinaryServiceUnavailableException(String message) {
        super(message);
    }

    public VeterinaryServiceUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}

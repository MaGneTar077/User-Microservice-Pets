package user.microservice.pets.domain.exceptions;

public class VeterinaryRejectedException extends RuntimeException {
    public VeterinaryRejectedException() {
        super("This clinic's registration was rejected");
    }
}

package user.microservice.pets.domain.exceptions;

public class VeterinarySuspendedException extends RuntimeException {
    public VeterinarySuspendedException() {
        super("This clinic is currently suspended");
    }
}

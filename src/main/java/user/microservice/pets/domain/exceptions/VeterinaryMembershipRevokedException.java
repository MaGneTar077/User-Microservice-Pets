package user.microservice.pets.domain.exceptions;

public class VeterinaryMembershipRevokedException extends RuntimeException {
    public VeterinaryMembershipRevokedException() {
        super("You are no longer an active member of this clinic");
    }
}

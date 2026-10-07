package user.microservice.pets.domain.exceptions;

public class NotVeterinaryMemberException extends RuntimeException {
    public NotVeterinaryMemberException() {
        super("You are not an active member of this clinic");
    }
}

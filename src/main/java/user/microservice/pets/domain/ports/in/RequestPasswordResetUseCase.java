package user.microservice.pets.domain.ports.in;

public interface RequestPasswordResetUseCase {
    void execute(String email);
}

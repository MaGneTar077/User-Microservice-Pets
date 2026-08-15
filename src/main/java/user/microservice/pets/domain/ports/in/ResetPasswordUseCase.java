package user.microservice.pets.domain.ports.in;

public interface ResetPasswordUseCase {
    void execute(String token, String newPassword);
}

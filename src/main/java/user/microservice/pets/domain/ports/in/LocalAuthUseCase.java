package user.microservice.pets.domain.ports.in;

import user.microservice.pets.domain.model.User;

public interface LocalAuthUseCase {
    User login(String email, String password);
}

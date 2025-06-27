package user.microservice.pets.domain.ports.in;

import user.microservice.pets.domain.model.User;

public interface RegisterUserUseCase {
    User register(User user);
}

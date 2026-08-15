package user.microservice.pets.domain.ports.in;

import user.microservice.pets.domain.model.User;

public interface UpdateUserProfileUseCase {
    User updateProfile(User user);
}

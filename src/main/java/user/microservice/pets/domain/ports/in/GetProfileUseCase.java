package user.microservice.pets.domain.ports.in;

import user.microservice.pets.domain.model.User;

import java.util.UUID;

public interface GetProfileUseCase {
    User getProfile(UUID userId);
}

package user.microservice.pets.domain.ports.in;

import java.util.UUID;

public interface DeleteProfileImageUseCase {
    void deleteProfileImage(UUID userId);
}

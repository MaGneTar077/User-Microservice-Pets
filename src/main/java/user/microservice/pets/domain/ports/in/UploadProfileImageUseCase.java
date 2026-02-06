package user.microservice.pets.domain.ports.in;

import user.microservice.pets.application.dto.ProfileImageUploadRequest;
import user.microservice.pets.application.dto.ProfileImageUploadResponse;

public interface UploadProfileImageUseCase {
    ProfileImageUploadResponse uploadProfileImage(ProfileImageUploadRequest request);
}

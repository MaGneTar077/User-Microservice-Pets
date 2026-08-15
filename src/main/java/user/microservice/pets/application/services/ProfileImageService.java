package user.microservice.pets.application.services;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;
import user.microservice.pets.application.dto.ProfileImageUploadRequest;
import user.microservice.pets.application.dto.ProfileImageUploadResponse;
import user.microservice.pets.domain.exceptions.InvalidFileException;
import user.microservice.pets.domain.ports.in.DeleteProfileImageUseCase;
import user.microservice.pets.domain.ports.in.UploadProfileImageUseCase;

import java.io.IOException;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class ProfileImageService {

    private final UploadProfileImageUseCase uploadProfileImageUseCase;
    private final DeleteProfileImageUseCase deleteProfileImageUseCase;

    public ProfileImageUploadResponse uploadProfileImage(UUID userId, MultipartFile file) {
        try {
            ProfileImageUploadRequest request = ProfileImageUploadRequest.builder()
                    .userId(userId.toString())
                    .fileName(file.getOriginalFilename())
                    .contentType(file.getContentType())
                    .fileSize(file.getSize())
                    .fileData(file.getBytes())
                    .build();

            return uploadProfileImageUseCase.uploadProfileImage(request);

        } catch (IOException e) {
            throw new InvalidFileException("Error al leer el archivo: " + e.getMessage());
        }
    }

    public void deleteProfileImage(UUID userId) {
        deleteProfileImageUseCase.deleteProfileImage(userId);
    }

}

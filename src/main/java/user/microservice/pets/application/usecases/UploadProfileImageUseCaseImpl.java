package user.microservice.pets.application.usecases;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import user.microservice.pets.application.dto.ProfileImageUploadRequest;
import user.microservice.pets.application.dto.ProfileImageUploadResponse;
import user.microservice.pets.application.services.FileValidationService;
import user.microservice.pets.domain.exceptions.FileUploadException;
import user.microservice.pets.domain.exceptions.UserNotFoundException;
import user.microservice.pets.domain.model.User;
import user.microservice.pets.domain.ports.in.UploadProfileImageUseCase;
import user.microservice.pets.domain.ports.out.StoragePort;
import user.microservice.pets.domain.ports.out.UserRepositoryPort;

import java.io.ByteArrayInputStream;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class UploadProfileImageUseCaseImpl implements UploadProfileImageUseCase {

    private final UserRepositoryPort userRepositoryPort;
    private final StoragePort storagePort;
    private final FileValidationService fileValidationService;

    private static final String BUCKET_NAME = "UserPetsProfilePicture";

    @Override
    @Transactional
    public ProfileImageUploadResponse uploadProfileImage(ProfileImageUploadRequest request) {
        try {

            UUID userId = UUID.fromString(request.getUserId());
            User user = userRepositoryPort.findById(userId)
                    .orElseThrow(() -> new UserNotFoundException("Usuario no encontrado con ID: " + request.getUserId()));

            fileValidationService.validateProfileImage(
                    request.getFileName(),
                    request.getContentType(),
                    request.getFileSize(),
                    request.getFileData()
            );

            if (user.getProfileImageUrl() != null && !user.getProfileImageUrl().isEmpty()) {
                try {
                    String oldFileName = extractFileNameFromUrl(user.getProfileImageUrl());
                    storagePort.deleteFile(BUCKET_NAME, oldFileName);
                } catch (Exception e) {
                    log.warn("No se pudo eliminar la imagen anterior: {}", e.getMessage());
                }
            }

            String uniqueFileName = fileValidationService.generateUniqueFileName(
                    request.getUserId(),
                    request.getFileName()
            );

            ByteArrayInputStream inputStream = new ByteArrayInputStream(request.getFileData());
            String imageUrl = storagePort.uploadFile(
                    BUCKET_NAME,
                    uniqueFileName,
                    inputStream,
                    request.getContentType(),
                    request.getFileSize()
            );

            userRepositoryPort.updateProfileImage(userId, imageUrl);

            log.info("Imagen de perfil subida exitosamente para el usuario: {}", userId);

            return ProfileImageUploadResponse.builder()
                    .imageUrl(imageUrl)
                    .message("Imagen de perfil actualizada exitosamente")
                    .success(true)
                    .build();

        } catch (UserNotFoundException e) {
            throw e;
        } catch (Exception e) {
            log.error("Error al subir imagen de perfil: {}", e.getMessage(), e);
            throw new FileUploadException("Error al subir la imagen de perfil: " + e.getMessage());
        }
    }

    private String extractFileNameFromUrl(String url) {
        int bucketIndex = url.indexOf(BUCKET_NAME);
        if (bucketIndex != -1) {
            return url.substring(bucketIndex + BUCKET_NAME.length() + 1);
        }
        int lastSlashIndex = url.lastIndexOf('/');
        return url.substring(lastSlashIndex + 1);
    }

}

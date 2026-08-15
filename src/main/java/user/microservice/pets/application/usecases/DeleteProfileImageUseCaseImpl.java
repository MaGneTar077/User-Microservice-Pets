package user.microservice.pets.application.usecases;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import user.microservice.pets.domain.exceptions.UserNotFoundException;
import user.microservice.pets.domain.model.User;
import user.microservice.pets.domain.ports.in.DeleteProfileImageUseCase;
import user.microservice.pets.domain.ports.out.StoragePort;
import user.microservice.pets.domain.ports.out.UserRepositoryPort;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class DeleteProfileImageUseCaseImpl implements DeleteProfileImageUseCase {

    private final UserRepositoryPort userRepositoryPort;
    private final StoragePort storagePort;

    private static final String BUCKET_NAME = "UserPetsProfilePicture";

    @Override
    @Transactional
    public void deleteProfileImage(UUID userId) {
        User user = userRepositoryPort.findById(userId)
                .orElseThrow(() -> new UserNotFoundException("Usuario no encontrado con ID: " + userId));

        if (user.getProfileImageUrl() != null && !user.getProfileImageUrl().isEmpty()) {
            try {
                String fileName = extractFileNameFromUrl(user.getProfileImageUrl());
                storagePort.deleteFile(BUCKET_NAME, fileName);
                userRepositoryPort.updateProfileImage(userId, null);
                log.info("Imagen de perfil eliminada para el usuario: {}", userId);
            } catch (Exception e) {
                log.error("Error al eliminar imagen de perfil: {}", e.getMessage(), e);
                throw new RuntimeException("Error al eliminar la imagen de perfil: " + e.getMessage());
            }
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

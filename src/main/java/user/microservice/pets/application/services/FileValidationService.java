package user.microservice.pets.application.services;

import org.springframework.stereotype.Component;
import user.microservice.pets.domain.exceptions.FileSizeExceededException;
import user.microservice.pets.domain.exceptions.InvalidFileException;
import user.microservice.pets.domain.exceptions.InvalidImageFormatException;

import java.util.Arrays;
import java.util.List;

@Component
public class FileValidationService {

    private static final long MAX_FILE_SIZE = 5 * 1024 * 1024; // 5MB
    private static final List<String> ALLOWED_CONTENT_TYPES = Arrays.asList(
            "image/jpeg",
            "image/jpg",
            "image/png",
            "image/webp"
    );
    private static final List<String> ALLOWED_EXTENSIONS = Arrays.asList(
            ".jpg", ".jpeg", ".png", ".webp"
    );

    public void validateProfileImage(String fileName, String contentType, long fileSize, byte[] fileData) {
        if (fileData == null || fileData.length == 0) {
            throw new InvalidFileException("El archivo está vacío");
        }

        if (fileSize > MAX_FILE_SIZE) {
            throw new FileSizeExceededException(
                    String.format("El archivo excede el tamaño máximo permitido de %d MB", MAX_FILE_SIZE / (1024 * 1024))
            );
        }

        if (contentType == null || !ALLOWED_CONTENT_TYPES.contains(contentType.toLowerCase())) {
            throw new InvalidImageFormatException(
                    "Formato de imagen no permitido. Solo se permiten: " + String.join(", ", ALLOWED_CONTENT_TYPES)
            );
        }

        if (fileName == null || fileName.isEmpty()) {
            throw new InvalidFileException("El nombre del archivo es inválido");
        }

        boolean hasValidExtension = ALLOWED_EXTENSIONS.stream()
                .anyMatch(ext -> fileName.toLowerCase().endsWith(ext));

        if (!hasValidExtension) {
            throw new InvalidImageFormatException(
                    "Extensión de archivo no permitida. Solo se permiten: " + String.join(", ", ALLOWED_EXTENSIONS)
            );
        }
    }

    public String generateUniqueFileName(String userId, String originalFileName) {
        String extension = getFileExtension(originalFileName);
        return String.format("%s/%s%s",
                userId,
                System.currentTimeMillis(),
                extension
        );
    }

    private String getFileExtension(String fileName) {
        int lastDotIndex = fileName.lastIndexOf('.');
        return (lastDotIndex == -1) ? "" : fileName.substring(lastDotIndex);
    }
}

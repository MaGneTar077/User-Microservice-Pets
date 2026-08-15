package user.microservice.pets.domain.ports.out;

import java.io.InputStream;

public interface StoragePort {
    String uploadFile(String bucketName, String fileName, InputStream fileStream, String contentType, long fileSize);
    void deleteFile(String bucketName, String fileName);
    String getPublicUrl(String bucketName, String fileName);
}

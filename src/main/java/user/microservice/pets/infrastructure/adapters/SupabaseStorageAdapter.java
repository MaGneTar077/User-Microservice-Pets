package user.microservice.pets.infrastructure.adapters;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import user.microservice.pets.domain.exceptions.FileUploadException;
import user.microservice.pets.domain.ports.out.StoragePort;

import java.io.IOException;
import java.io.InputStream;

@Slf4j
@Component
@RequiredArgsConstructor
public class SupabaseStorageAdapter implements StoragePort {

    private final RestTemplate restTemplate;

    @Value("${supabase.url}")
    private String supabaseUrl;

    @Value("${supabase.key}")
    private String supabaseKey;

    @Override
    public String uploadFile(String bucketName, String fileName, InputStream fileStream, String contentType, long fileSize) {
        try {
            byte[] fileBytes = fileStream.readAllBytes();

            String uploadUrl = String.format("%s/storage/v1/object/%s/%s",
                    supabaseUrl, bucketName, fileName);

            HttpHeaders headers = new HttpHeaders();
            headers.set("Authorization", "Bearer " + supabaseKey);
            headers.setContentType(MediaType.parseMediaType(contentType));
            headers.set("apikey", supabaseKey);

            HttpEntity<byte[]> requestEntity = new HttpEntity<>(fileBytes, headers);

            ResponseEntity<String> response = restTemplate.exchange(
                    uploadUrl,
                    HttpMethod.POST,
                    requestEntity,
                    String.class
            );

            if (response.getStatusCode().is2xxSuccessful()) {
                String publicUrl = getPublicUrl(bucketName, fileName);
                log.info("Archivo subido exitosamente a Supabase: {}", publicUrl);
                return publicUrl;
            } else {
                throw new FileUploadException("Error al subir archivo a Supabase: " + response.getStatusCode());
            }

        } catch (IOException e) {
            log.error("Error al leer el archivo: {}", e.getMessage(), e);
            throw new FileUploadException("Error al leer el archivo", e);
        } catch (Exception e) {
            log.error("Error al subir archivo a Supabase: {}", e.getMessage(), e);
            throw new FileUploadException("Error al subir archivo a Supabase", e);
        }
    }

    @Override
    public void deleteFile(String bucketName, String fileName) {
        try {
            String deleteUrl = String.format("%s/storage/v1/object/%s/%s",
                    supabaseUrl, bucketName, fileName);

            HttpHeaders headers = new HttpHeaders();
            headers.set("Authorization", "Bearer " + supabaseKey);
            headers.set("apikey", supabaseKey);

            HttpEntity<Void> requestEntity = new HttpEntity<>(headers);

            restTemplate.exchange(
                    deleteUrl,
                    HttpMethod.DELETE,
                    requestEntity,
                    String.class
            );

            log.info("Archivo eliminado de Supabase: {}", fileName);

        } catch (Exception e) {
            log.error("Error al eliminar archivo de Supabase: {}", e.getMessage(), e);
            throw new FileUploadException("Error al eliminar archivo de Supabase", e);
        }
    }

    @Override
    public String getPublicUrl(String bucketName, String fileName) {
        return String.format("%s/storage/v1/object/public/%s/%s",
                supabaseUrl, bucketName, fileName);
    }
}

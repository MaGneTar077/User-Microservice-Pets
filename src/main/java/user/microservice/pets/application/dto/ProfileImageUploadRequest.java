package user.microservice.pets.application.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class ProfileImageUploadRequest {
    private String userId;
    private String fileName;
    private String contentType;
    private long fileSize;
    private byte[] fileData;
}

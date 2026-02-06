package user.microservice.pets.application.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class ProfileImageUploadResponse {
    private String imageUrl;
    private String message;
    private boolean success;
}

package user.microservice.pets.application.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import user.microservice.pets.domain.enums.AuthProvider;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class ProfileResponse {
    private UUID id;
    private String username;
    private String email;
    private LocalDateTime createdAt;
    private AuthProvider authProvider;
}

package user.microservice.pets.application.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.UUID;
import java.time.LocalDateTime;

@Data
@AllArgsConstructor
public class RegisterResponse {
    private UUID uuid;
    private String username;
    private String email;
    private LocalDateTime createdAt;
}

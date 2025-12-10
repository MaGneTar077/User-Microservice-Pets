package user.microservice.pets.application.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.UUID;

@Data
@AllArgsConstructor
public class UpdateUserResponse {
    private UUID id;
    private String username;
    private String email;
}

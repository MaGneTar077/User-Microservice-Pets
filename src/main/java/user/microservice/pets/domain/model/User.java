package user.microservice.pets.domain.model;

import java.time.LocalDateTime;
import java.util.UUID;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import user.microservice.pets.domain.enums.AuthProvider;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class User {

    private UUID id;
    private String username;
    private String email;
    private String password;
    private String profileImageUrl;
    private LocalDateTime createdAt;
    private AuthProvider authProvider;

}

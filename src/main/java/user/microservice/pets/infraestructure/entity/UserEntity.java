package user.microservice.pets.infraestructure.entity;

import jakarta.persistence.*;
import lombok.*;
import user.microservice.pets.domain.enums.AuthProvider;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "users")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserEntity {

    @Id
    private UUID id;

    private String username;
    private String email;
    private String password;
    private LocalDateTime createdAt;

    @Enumerated(EnumType.STRING)
    private AuthProvider authProvider;

}

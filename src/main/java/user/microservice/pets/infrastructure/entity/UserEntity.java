package user.microservice.pets.infrastructure.entity;

import jakarta.persistence.*;
import lombok.*;
import user.microservice.pets.domain.enums.AuthProvider;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "users")
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class UserEntity {

    @Id
    private UUID id;
    private String username;

    @Column(unique = true, nullable = false)
    private String email;
    private String password;

    @Enumerated(EnumType.STRING)
    @Column(name = "auth_provider", nullable = false)
    private AuthProvider authProvider;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

}

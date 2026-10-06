package user.microservice.pets.domain.model;

import java.time.LocalDateTime;
import java.util.UUID;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import user.microservice.pets.domain.enums.TokenContext;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class RefreshToken {

    private UUID id;
    private UUID userId;
    private String tokenHash;
    private TokenContext context;
    private UUID veterinaryId;
    private LocalDateTime expiresAt;
    private LocalDateTime revokedAt;
    private UUID replacedBy;
    private LocalDateTime createdAt;

}

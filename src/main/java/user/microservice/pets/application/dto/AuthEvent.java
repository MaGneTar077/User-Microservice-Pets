package user.microservice.pets.application.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class AuthEvent {
    private UUID userId;
    private String email;
    private String eventType;
    private Instant occurredAt;
}

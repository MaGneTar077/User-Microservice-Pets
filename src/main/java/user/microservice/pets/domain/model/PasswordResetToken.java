package user.microservice.pets.domain.model;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class PasswordResetToken {
    private Long id;
    private String token;
    private String email;
    private LocalDateTime expiresAt;
    private LocalDateTime createdAt;
    private Boolean used;

    public PasswordResetToken(String token, String email, LocalDateTime expiresAt) {
        this.token = token;
        this.email = email;
        this.expiresAt = expiresAt;
        this.createdAt = LocalDateTime.now();
        this.used = false;
    }

    public boolean isUsed() {
        return this.used != null && this.used;
    }
}
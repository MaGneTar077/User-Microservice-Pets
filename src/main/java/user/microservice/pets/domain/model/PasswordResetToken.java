package user.microservice.pets.domain.model;

import java.time.LocalDateTime;

public class PasswordResetToken {
    private String token;
    private String email;
    private LocalDateTime expiresAt;

    public PasswordResetToken(String token, String email, LocalDateTime expiresAt) {
        this.token = token;
        this.email = email;
        this.expiresAt = expiresAt;
    }

    public String getToken() { return token; }
    public String getEmail() { return email; }
    public LocalDateTime getExpiresAt() { return expiresAt; }

    public boolean isExpired() {
        return LocalDateTime.now().isAfter(expiresAt);
    }
}

package user.microservice.pets.application.services;

import io.jsonwebtoken.Claims;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import user.microservice.pets.domain.exceptions.InvalidTokenException;
import user.microservice.pets.infrastructure.security.JwtUtil;

import java.util.Date;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
@RequiredArgsConstructor
public class LogoutService {

    private final JwtUtil jwtUtil;

    private final Map<String, Date> invalidTokens = new ConcurrentHashMap<>();

    public void logout(String token) {
        if (token == null || token.trim().isEmpty()) {
            throw new InvalidTokenException("Token cannot be empty");
        }

        if (token.split("\\.").length != 3) {
            throw new InvalidTokenException("Invalid token format");
        }

        try {
            Claims claims = jwtUtil.validateToken(token);
            Date expiration = claims.getExpiration();

            if (expiration.before(new Date())) {
                log.warn("Attempt to logout with already expired token");
                throw new InvalidTokenException("Token is already expired");
            }

            if (isTokenInvalid(token)) {
                log.warn("Attempt to logout with already invalidated token");
                throw new InvalidTokenException("Token is already invalidated");
            }

            invalidTokens.put(token, expiration);
            log.info("Token invalidated successfully for user: {}", claims.getSubject());

        } catch (io.jsonwebtoken.ExpiredJwtException e) {
            log.warn("Attempt to logout with expired token: {}", e.getMessage());
            throw new InvalidTokenException("Token is expired");
        } catch (io.jsonwebtoken.MalformedJwtException e) {
            log.warn("Attempt to logout with malformed token: {}", e.getMessage());
            throw new InvalidTokenException("Malformed token");
        } catch (io.jsonwebtoken.security.SignatureException e) {
            log.warn("Attempt to logout with invalid signature: {}", e.getMessage());
            throw new InvalidTokenException("Invalid token signature");
        } catch (Exception e) {
            log.error("Error during logout: {}", e.getMessage());
            throw new InvalidTokenException("Invalid token");
        }
    }

    public boolean isTokenInvalid(String token) {
        if (token == null || token.trim().isEmpty()) {
            return false;
        }
        return invalidTokens.containsKey(token);
    }

    @Scheduled(fixedRate = 3600000) // cada 1 hora
    public void cleanupExpiredTokens() {
        Date now = new Date();
        int initialSize = invalidTokens.size();

        invalidTokens.entrySet().removeIf(entry -> entry.getValue().before(now));

        int removedCount = initialSize - invalidTokens.size();
        if (removedCount > 0) {
            log.info("Cleaned up {} expired tokens from blacklist. Current size: {}",
                    removedCount, invalidTokens.size());
        }
    }

    public int getBlacklistSize() {
        return invalidTokens.size();
    }
}
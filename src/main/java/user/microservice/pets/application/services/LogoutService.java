package user.microservice.pets.application.services;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import user.microservice.pets.domain.exceptions.InvalidTokenException;
import user.microservice.pets.infrastructure.security.JwtUtil;

import java.time.Duration;
import java.util.Date;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
@RequiredArgsConstructor
public class LogoutService {

    public static final String BLACKLIST_PREFIX = "jwt:blacklist:";

    private final JwtUtil jwtUtil;
    private final StringRedisTemplate redisTemplate;

    private final Map<String, Date> invalidTokens = new ConcurrentHashMap<>();

    public Claims logout(String token) {
        if (token == null || token.isBlank()) {
            throw new InvalidTokenException("Token cannot be empty");
        }
        if (token.split("\\.").length != 3) {
            throw new InvalidTokenException("Invalid token format");
        }

        Claims claims;
        try {
            claims = jwtUtil.validateToken(token);
        } catch (ExpiredJwtException e) {
            log.warn("Attempt to logout with expired token");
            throw new InvalidTokenException("Token is expired");
        } catch (JwtException | IllegalArgumentException e) {
            log.warn("Attempt to logout with invalid token: {}", e.getMessage());
            throw new InvalidTokenException("Invalid token");
        }

        long ttlMs = claims.getExpiration().getTime() - System.currentTimeMillis();
        if (ttlMs <= 0) {
            throw new InvalidTokenException("Token is already expired");
        }

        // setIfAbsent: solo lo guarda si no estaba ya invalidado
        Boolean stored = redisTemplate.opsForValue()
                .setIfAbsent(BLACKLIST_PREFIX + token, "1", Duration.ofMillis(ttlMs));

        if (!Boolean.TRUE.equals(stored)) {
            log.warn("Attempt to logout with already invalidated token");
            throw new InvalidTokenException("Token is already invalidated");
        }

        log.info("Token invalidated successfully for user: {}", claims.getSubject());
        return claims;
    }

    public boolean isTokenInvalid(String token) {
        if (token == null || token.isBlank()) {
            return false;
        }
        return Boolean.TRUE.equals(redisTemplate.hasKey(BLACKLIST_PREFIX + token));
    }
}
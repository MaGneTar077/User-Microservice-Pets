package user.microservice.pets.application.services;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import user.microservice.pets.domain.enums.AuthProvider;
import user.microservice.pets.domain.exceptions.InvalidVerificationCodeException;
import user.microservice.pets.domain.model.User;
import user.microservice.pets.domain.ports.out.EmailSenderPort;
import user.microservice.pets.domain.ports.out.UserRepositoryPort;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmailVerificationService {

    private static final String CODE_PREFIX = "email:verify:code:";
    private static final String ATTEMPTS_PREFIX = "email:verify:attempts:";
    private static final String COOLDOWN_PREFIX = "email:verify:cooldown:";

    private static final Duration CODE_TTL = Duration.ofMinutes(15);
    private static final Duration RESEND_COOLDOWN = Duration.ofSeconds(60);
    private static final int MAX_ATTEMPTS = 5;

    private final StringRedisTemplate redisTemplate;
    private final EmailSenderPort emailSenderPort;
    private final UserRepositoryPort userRepositoryPort;

    private final SecureRandom random = new SecureRandom();

    public void sendCode(User user) {
        String email = user.getEmail();
        String code = String.format("%06d", random.nextInt(1_000_000));

        redisTemplate.opsForValue().set(CODE_PREFIX + email, code, CODE_TTL);
        redisTemplate.opsForValue().set(COOLDOWN_PREFIX + email, "1", RESEND_COOLDOWN);
        redisTemplate.delete(ATTEMPTS_PREFIX + email);

        log.info("Sending verification code to {}", email);
        try {
            emailSenderPort.sendEmail(
                    email,
                    "Verifica tu correo en MyAnimaLog",
                    buildBody(user.getUsername(), code));
            log.info("Verification code sent to {}", email);
        } catch (Exception e) {
            log.error("Could not send verification email to {}", email, e);
        }
    }

    public void resend(String rawEmail) {
        String email = normalize(rawEmail);
        log.info("Resend requested for [{}]", email);

        if (email == null) {
            log.info("Resend skipped: empty email");
            return;
        }

        Boolean allowed = redisTemplate.opsForValue()
                .setIfAbsent(COOLDOWN_PREFIX + email, "1", RESEND_COOLDOWN);
        if (!Boolean.TRUE.equals(allowed)) {
            Long ttl = redisTemplate.getExpire(COOLDOWN_PREFIX + email);
            log.info("Resend skipped for {}: cooldown active ({} s left)", email, ttl);
            return;
        }

        Optional<User> userOpt = userRepositoryPort.findByEmail(email);
        if (userOpt.isEmpty()) {
            log.info("Resend skipped for {}: user not found", email);
            return;
        }

        User user = userOpt.get();
        if (user.getAuthProvider() != AuthProvider.LOCAL) {
            log.info("Resend skipped for {}: provider is {}", email, user.getAuthProvider());
            return;
        }
        if (user.isEmailVerified()) {
            log.info("Resend skipped for {}: already verified", email);
            return;
        }

        sendCode(user);
    }

    public void verify(String rawEmail, String code) {
        String email = normalize(rawEmail);
        if (email == null || code == null || !code.matches("\\d{6}")) {
            throw new InvalidVerificationCodeException();
        }

        String attemptsKey = ATTEMPTS_PREFIX + email;
        Long attempts = redisTemplate.opsForValue().increment(attemptsKey);
        if (attempts != null && attempts == 1) {
            redisTemplate.expire(attemptsKey, CODE_TTL);
        }
        if (attempts != null && attempts > MAX_ATTEMPTS) {
            redisTemplate.delete(CODE_PREFIX + email);
            throw new InvalidVerificationCodeException("Too many attempts. Request a new code");
        }

        String stored = redisTemplate.opsForValue().get(CODE_PREFIX + email);
        if (stored == null || !MessageDigest.isEqual(
                stored.getBytes(StandardCharsets.UTF_8),
                code.getBytes(StandardCharsets.UTF_8))) {
            throw new InvalidVerificationCodeException();
        }

        User user = userRepositoryPort.findByEmail(email)
                .orElseThrow(InvalidVerificationCodeException::new);

        if (!user.isEmailVerified()) {
            user.setEmailVerified(true);
            userRepositoryPort.save(user);
        }

        redisTemplate.delete(List.of(CODE_PREFIX + email, attemptsKey));
        log.info("Email verified for {}", email);
    }

    private String buildBody(String username, String code) {
        return """
            Hola %s,

            Tu código para verificar tu correo en MyAnimaLog es:

            %s

            El código vence en 15 minutos.
            Si no creaste una cuenta, ignora este mensaje.

            Saludos,
            El equipo de MyAnimaLog
            """.formatted(username != null ? username : "", code);
    }

    private String normalize(String email) {
        return (email == null || email.isBlank()) ? null : email.trim().toLowerCase(Locale.ROOT);
    }
}
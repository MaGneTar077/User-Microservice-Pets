package user.microservice.pets.application.services;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import user.microservice.pets.domain.enums.TokenContext;
import user.microservice.pets.domain.exceptions.InvalidTokenException;
import user.microservice.pets.domain.model.RefreshToken;
import user.microservice.pets.domain.ports.out.RefreshTokenRepositoryPort;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("RefreshTokenService - Unit Tests")
class RefreshTokenServiceTest {

    @Mock
    private RefreshTokenRepositoryPort refreshTokenRepository;

    private RefreshTokenService service;

    @BeforeEach
    void setUp() {
        service = new RefreshTokenService(refreshTokenRepository);
    }

    @Test
    @DisplayName("issue() should persist only the hash of the token, never the raw value")
    void issueShouldPersistOnlyTheHash() {
        UUID userId = UUID.randomUUID();
        ArgumentCaptor<RefreshToken> captor = ArgumentCaptor.forClass(RefreshToken.class);
        when(refreshTokenRepository.save(captor.capture())).thenAnswer(inv -> inv.getArgument(0));

        String rawToken = service.issue(userId);

        assertThat(rawToken).isNotBlank();
        RefreshToken saved = captor.getValue();
        assertThat(saved.getUserId()).isEqualTo(userId);
        assertThat(saved.getContext()).isEqualTo(TokenContext.USER);
        assertThat(saved.getTokenHash()).isNotEqualTo(rawToken);
        assertThat(saved.getTokenHash()).hasSize(64); // SHA-256 en hex
        assertThat(saved.getExpiresAt()).isAfter(LocalDateTime.now().plusDays(29));
    }

    @Test
    @DisplayName("rotate() should revoke the used token and issue a new one for the same user")
    void rotateShouldRevokeUsedTokenAndIssueNewOne() {
        UUID userId = UUID.randomUUID();
        ArgumentCaptor<RefreshToken> captor = ArgumentCaptor.forClass(RefreshToken.class);
        when(refreshTokenRepository.save(captor.capture())).thenAnswer(inv -> inv.getArgument(0));

        String rawToken = service.issue(userId);
        RefreshToken storedToken = captor.getValue();
        reset(refreshTokenRepository);

        when(refreshTokenRepository.findByTokenHash(storedToken.getTokenHash()))
                .thenReturn(Optional.of(storedToken));
        ArgumentCaptor<RefreshToken> saveCaptor = ArgumentCaptor.forClass(RefreshToken.class);
        when(refreshTokenRepository.save(saveCaptor.capture())).thenAnswer(inv -> inv.getArgument(0));

        RefreshTokenService.RotatedRefreshToken rotated = service.rotate(rawToken);

        assertThat(rotated.userId()).isEqualTo(userId);
        assertThat(rotated.rawToken()).isNotBlank().isNotEqualTo(rawToken);

        verify(refreshTokenRepository, never()).revokeAllActiveForUser(any());
        // Dos saves: el refresh nuevo, y el viejo marcado como revocado/reemplazado.
        assertThat(saveCaptor.getAllValues()).hasSize(2);
        assertThat(storedToken.getRevokedAt()).isNotNull();
        assertThat(storedToken.getReplacedBy()).isNotNull();
    }

    @Test
    @DisplayName("rotate() reusing an already-revoked token should revoke the whole chain and reject it")
    void rotateWithRevokedTokenShouldRevokeWholeChain() {
        UUID userId = UUID.randomUUID();
        RefreshToken revokedToken = RefreshToken.builder()
                .id(UUID.randomUUID())
                .userId(userId)
                .tokenHash("somehash")
                .context(TokenContext.USER)
                .expiresAt(LocalDateTime.now().plusDays(10))
                .revokedAt(LocalDateTime.now().minusMinutes(5))
                .createdAt(LocalDateTime.now().minusDays(1))
                .build();

        when(refreshTokenRepository.findByTokenHash(any())).thenReturn(Optional.of(revokedToken));

        assertThatThrownBy(() -> service.rotate("whatever-raw-token"))
                .isInstanceOf(InvalidTokenException.class);

        verify(refreshTokenRepository).revokeAllActiveForUser(userId);
        verify(refreshTokenRepository, never()).save(any());
    }

    @Test
    @DisplayName("rotate() with an expired (but not revoked) token should be rejected without touching other sessions")
    void rotateWithExpiredTokenShouldBeRejected() {
        UUID userId = UUID.randomUUID();
        RefreshToken expiredToken = RefreshToken.builder()
                .id(UUID.randomUUID())
                .userId(userId)
                .tokenHash("somehash")
                .context(TokenContext.USER)
                .expiresAt(LocalDateTime.now().minusMinutes(1))
                .createdAt(LocalDateTime.now().minusDays(31))
                .build();

        when(refreshTokenRepository.findByTokenHash(any())).thenReturn(Optional.of(expiredToken));

        assertThatThrownBy(() -> service.rotate("whatever-raw-token"))
                .isInstanceOf(InvalidTokenException.class);

        verify(refreshTokenRepository, never()).revokeAllActiveForUser(any());
        verify(refreshTokenRepository, never()).save(any());
    }

    @Test
    @DisplayName("rotate() with an unknown token should be rejected")
    void rotateWithUnknownTokenShouldBeRejected() {
        when(refreshTokenRepository.findByTokenHash(any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.rotate("unknown-token"))
                .isInstanceOf(InvalidTokenException.class);
    }

    @Test
    @DisplayName("revoke() should mark a known, not-yet-revoked token as revoked")
    void revokeShouldMarkTokenAsRevoked() {
        RefreshToken activeToken = RefreshToken.builder()
                .id(UUID.randomUUID())
                .userId(UUID.randomUUID())
                .tokenHash("somehash")
                .context(TokenContext.USER)
                .expiresAt(LocalDateTime.now().plusDays(10))
                .createdAt(LocalDateTime.now())
                .build();

        when(refreshTokenRepository.findByTokenHash(any())).thenReturn(Optional.of(activeToken));

        service.revoke("some-raw-token");

        assertThat(activeToken.getRevokedAt()).isNotNull();
        verify(refreshTokenRepository).save(activeToken);
    }

    @Test
    @DisplayName("revoke() should do nothing for a blank or unknown token (best-effort, used from logout)")
    void revokeShouldBeNoOpForBlankOrUnknownToken() {
        service.revoke(null);
        service.revoke("");

        when(refreshTokenRepository.findByTokenHash(any())).thenReturn(Optional.empty());
        service.revoke("unknown-token");

        verify(refreshTokenRepository, never()).save(any());
    }
}

package user.microservice.pets.infrastructure.security;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import java.io.IOException;
import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("JwtKeyProvider - Unit Tests")
class JwtKeyProviderTest {

    private static final Path DEV_KEY_DIR = Path.of(".local-keys");

    @AfterEach
    void cleanupGeneratedDevKey() throws IOException {
        Path devKeyFile = DEV_KEY_DIR.resolve("jwt-dev-private-key.pem");
        if (Files.exists(DEV_KEY_DIR)) {
            Files.walk(DEV_KEY_DIR)
                    .sorted(Comparator.reverseOrder())
                    .forEach(path -> {
                        try {
                            Files.deleteIfExists(path);
                        } catch (IOException ignored) {
                        }
                    });
        }
        assertThat(Files.exists(devKeyFile)).isFalse();
    }

    @Test
    @DisplayName("Should fail fast when JWT_PRIVATE_KEY_PEM is missing outside the dev profile")
    void shouldFailFastWithoutPrivateKeyOutsideDevProfile() {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("prod");

        assertThatThrownBy(() -> new JwtKeyProvider("", "key-1", environment))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("JWT_PRIVATE_KEY_PEM");
    }

    @Test
    @DisplayName("Should generate and persist a dev RSA key when no profile is active (today's default)")
    void shouldGenerateDevKeyWhenNoProfileIsActive() {
        MockEnvironment environment = new MockEnvironment();

        JwtKeyProvider provider = new JwtKeyProvider("", "dev-key-1", environment);

        assertThat(provider.getPrivateKey()).isNotNull();
        assertThat(provider.getPublicKey()).isNotNull();
        assertThat(Files.exists(DEV_KEY_DIR.resolve("jwt-dev-private-key.pem"))).isTrue();
    }

    @Test
    @DisplayName("Should reuse the same dev key across restarts instead of generating a new one each time")
    void shouldReuseDevKeyAcrossInstances() {
        MockEnvironment environment = new MockEnvironment();

        JwtKeyProvider first = new JwtKeyProvider("", "dev-key-1", environment);
        BigInteger firstModulus = first.getPublicKey().getModulus();

        JwtKeyProvider second = new JwtKeyProvider("", "dev-key-1", environment);
        BigInteger secondModulus = second.getPublicKey().getModulus();

        assertThat(secondModulus).isEqualTo(firstModulus);
    }

    @Test
    @DisplayName("Should accept an explicit PEM key regardless of active profile")
    void shouldAcceptExplicitPemRegardlessOfProfile() throws Exception {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("prod");

        String pem = TestRsaKeys.generatePrivateKeyPem();

        JwtKeyProvider provider = new JwtKeyProvider(pem, "prod-key-1", environment);

        assertThat(provider.getPrivateKey()).isNotNull();
        assertThat(provider.getPublicKey()).isNotNull();
        assertThat(provider.getKeyId()).isEqualTo("prod-key-1");
    }
}

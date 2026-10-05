package user.microservice.pets.infrastructure.controllers;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.env.MockEnvironment;
import user.microservice.pets.infrastructure.security.JwtKeyProvider;
import user.microservice.pets.infrastructure.security.TestRsaKeys;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("JwksController - Unit Tests")
class JwksControllerTest {

    @Test
    @DisplayName("GET /.well-known/jwks.json exposes only the public key, cacheable for 10 minutes")
    void shouldExposeOnlyThePublicKey() throws Exception {
        String pem = TestRsaKeys.generatePrivateKeyPem();
        JwtKeyProvider jwtKeyProvider = new JwtKeyProvider(pem, "test-key-1", new MockEnvironment());
        JwksController controller = new JwksController(jwtKeyProvider);

        ResponseEntity<Map<String, Object>> response = controller.jwks();

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getHeaders().getCacheControl()).contains("max-age=600").contains("public");

        Map<String, Object> body = response.getBody();
        assertThat(body).isNotNull().containsKey("keys");

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> keys = (List<Map<String, Object>>) body.get("keys");
        assertThat(keys).hasSize(1);

        Map<String, Object> key = keys.get(0);
        assertThat(key.get("kty")).isEqualTo("RSA");
        assertThat(key.get("use")).isEqualTo("sig");
        assertThat(key.get("alg")).isEqualTo("RS256");
        assertThat(key.get("kid")).isEqualTo("test-key-1");
        assertThat(key).containsKeys("n", "e");
        // Nunca debe filtrarse material de la llave privada (d, p, q, etc).
        assertThat(key).doesNotContainKeys("d", "p", "q", "dp", "dq", "qi");
    }
}

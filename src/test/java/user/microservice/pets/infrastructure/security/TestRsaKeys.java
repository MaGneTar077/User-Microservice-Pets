package user.microservice.pets.infrastructure.security;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.util.Base64;

/**
 * Genera llaves RSA de prueba en memoria (sin tocar el filesystem), usadas por los tests de
 * JwtKeyProvider/JwtUtil/JwksController para simular JWT_PRIVATE_KEY_PEM.
 */
public final class TestRsaKeys {

    private TestRsaKeys() {
    }

    public static String generatePrivateKeyPem() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        KeyPair keyPair = generator.generateKeyPair();

        String base64 = Base64.getEncoder().encodeToString(keyPair.getPrivate().getEncoded());
        StringBuilder sb = new StringBuilder("-----BEGIN PRIVATE KEY-----\n");
        for (int i = 0; i < base64.length(); i += 64) {
            sb.append(base64, i, Math.min(i + 64, base64.length())).append("\n");
        }
        sb.append("-----END PRIVATE KEY-----\n");
        return sb.toString();
    }
}

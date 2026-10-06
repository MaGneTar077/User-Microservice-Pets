package user.microservice.pets.infrastructure.security;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.interfaces.RSAPrivateCrtKey;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.RSAPublicKeySpec;
import java.util.Arrays;
import java.util.Base64;

/**
 * Carga la llave RSA usada para firmar JWT (RS256).
 * <p>
 * En cualquier perfil que NO sea "dev", {@code JWT_PRIVATE_KEY_PEM} es obligatoria y la app
 * no arranca sin ella. En "dev" (o cuando no hay ningún perfil activo, que es como corre hoy
 * este servicio) se genera un par RSA-2048 una sola vez y se persiste fuera de Git para que
 * los tokens sobrevivan reinicios.
 */
@Slf4j
@Component
public class JwtKeyProvider {

    private static final String DEV_PROFILE = "dev";
    private static final Path DEV_KEY_PATH = Path.of(".local-keys", "jwt-dev-private-key.pem");

    @Getter
    private final RSAPrivateKey privateKey;

    @Getter
    private final RSAPublicKey publicKey;

    @Getter
    private final String keyId;

    public JwtKeyProvider(
            @Value("${jwt.private-key-pem:}") String privateKeyPemProperty,
            @Value("${jwt.key-id:dev-key-1}") String keyId,
            Environment environment) {
        this.keyId = keyId;

        String pem = privateKeyPemProperty;
        if (pem == null || pem.isBlank()) {
            if (isDevLike(environment)) {
                pem = loadOrGenerateDevKeyPem();
            } else {
                throw new IllegalStateException(
                        "JWT_PRIVATE_KEY_PEM no esta configurada. Es obligatoria fuera del perfil 'dev' " +
                                "(perfiles activos: " + Arrays.toString(environment.getActiveProfiles()) + "). " +
                                "Genera un par RSA-2048 y configura la llave privada (PKCS8, PEM) como secreto.");
            }
        }

        KeyPair keyPair = parsePrivateKeyPem(pem);
        this.privateKey = (RSAPrivateKey) keyPair.getPrivate();
        this.publicKey = (RSAPublicKey) keyPair.getPublic();
    }

    /**
     * Hoy este servicio no define ningun perfil de Spring (arranca sin SPRING_PROFILES_ACTIVE).
     * Para no romper ese flujo actual, "sin perfil activo" se trata igual que "dev". El gate de
     * produccion real se activa recien cuando se fija explicitamente un perfil distinto de "dev"
     * (p. ej. SPRING_PROFILES_ACTIVE=prod en el despliegue).
     */
    private boolean isDevLike(Environment environment) {
        String[] activeProfiles = environment.getActiveProfiles();
        return activeProfiles.length == 0 || Arrays.asList(activeProfiles).contains(DEV_PROFILE);
    }

    private String loadOrGenerateDevKeyPem() {
        try {
            if (Files.exists(DEV_KEY_PATH)) {
                log.info("Cargando llave RSA de desarrollo existente desde {}", DEV_KEY_PATH);
                return Files.readString(DEV_KEY_PATH);
            }

            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            KeyPair keyPair = generator.generateKeyPair();
            String generatedPem = toPem(keyPair.getPrivate());

            Files.createDirectories(DEV_KEY_PATH.getParent());
            Files.writeString(DEV_KEY_PATH, generatedPem);
            log.warn("JWT_PRIVATE_KEY_PEM no configurada: se genero una nueva llave RSA de desarrollo en {}. " +
                    "Este archivo no debe commitearse (esta en .gitignore). No usar este modo en produccion.",
                    DEV_KEY_PATH);
            return generatedPem;
        } catch (NoSuchAlgorithmException | IOException e) {
            throw new IllegalStateException("No se pudo generar/cargar la llave RSA de desarrollo", e);
        }
    }

    private String toPem(PrivateKey key) {
        String base64 = Base64.getEncoder().encodeToString(key.getEncoded());
        StringBuilder sb = new StringBuilder("-----BEGIN PRIVATE KEY-----\n");
        for (int i = 0; i < base64.length(); i += 64) {
            sb.append(base64, i, Math.min(i + 64, base64.length())).append("\n");
        }
        sb.append("-----END PRIVATE KEY-----\n");
        return sb.toString();
    }

    private KeyPair parsePrivateKeyPem(String pem) {
        String normalized = pem
                .replace("\\n", "\n")
                .replace("-----BEGIN PRIVATE KEY-----", "")
                .replace("-----END PRIVATE KEY-----", "")
                .replaceAll("\\s", "");

        try {
            byte[] der = Base64.getDecoder().decode(normalized);
            PrivateKey parsed = KeyFactory.getInstance("RSA")
                    .generatePrivate(new PKCS8EncodedKeySpec(der));

            if (!(parsed instanceof RSAPrivateCrtKey crtKey)) {
                throw new IllegalStateException(
                        "La llave privada debe ser RSA en formato PKCS8 con parametros CRT");
            }

            RSAPublicKey derivedPublicKey = (RSAPublicKey) KeyFactory.getInstance("RSA")
                    .generatePublic(new RSAPublicKeySpec(crtKey.getModulus(), crtKey.getPublicExponent()));

            return new KeyPair(derivedPublicKey, crtKey);
        } catch (IllegalStateException e) {
            throw e;
        } catch (IllegalArgumentException | InvalidKeySpecException | NoSuchAlgorithmException e) {
            throw new IllegalStateException("JWT_PRIVATE_KEY_PEM invalida: no es una llave RSA PKCS8 valida", e);
        }
    }
}

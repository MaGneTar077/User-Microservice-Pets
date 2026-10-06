package user.microservice.pets.infrastructure.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwsHeader;
import io.jsonwebtoken.SigningKeyResolverAdapter;
import io.jsonwebtoken.UnsupportedJwtException;

import java.security.Key;

/**
 * Resuelve la llave de verificacion segun el token:
 * - Tokens nuevos llevan header "kid" (se firman con {@link JwtKeyProvider}) -> llave publica RSA.
 * - Tokens viejos (emitidos antes de la migracion a RS256) no tienen "kid" -> llave HMAC legada,
 *   solo mientras {@code JWT_ACCEPT_LEGACY=true}.
 * <p>
 * TODO: eliminar la rama legada (y la propiedad jwt.accept-legacy) una vez haya pasado la
 * duracion maxima de los tokens HS256 emitidos antes de esta migracion (1 hora desde el deploy).
 */
public class JwtSigningKeyResolver extends SigningKeyResolverAdapter {

    private final Key rsaPublicKey;
    private final Key legacyKey;
    private final boolean acceptLegacy;

    public JwtSigningKeyResolver(Key rsaPublicKey, Key legacyKey, boolean acceptLegacy) {
        this.rsaPublicKey = rsaPublicKey;
        this.legacyKey = legacyKey;
        this.acceptLegacy = acceptLegacy;
    }

    @Override
    public Key resolveSigningKey(JwsHeader header, Claims claims) {
        if (header.getKeyId() != null) {
            return rsaPublicKey;
        }
        if (!acceptLegacy) {
            throw new UnsupportedJwtException(
                    "Tokens HS256 sin 'kid' ya no se aceptan (JWT_ACCEPT_LEGACY=false)");
        }
        return legacyKey;
    }
}

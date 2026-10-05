package user.microservice.pets.infrastructure.controllers;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.KeyUse;
import com.nimbusds.jose.jwk.RSAKey;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import user.microservice.pets.infrastructure.security.JwtKeyProvider;

import java.util.Map;
import java.util.concurrent.TimeUnit;

@RestController
@RequiredArgsConstructor
public class JwksController {

    private final JwtKeyProvider jwtKeyProvider;

    @GetMapping("/.well-known/jwks.json")
    public ResponseEntity<Map<String, Object>> jwks() {
        RSAKey rsaKey = new RSAKey.Builder(jwtKeyProvider.getPublicKey())
                .keyID(jwtKeyProvider.getKeyId())
                .keyUse(KeyUse.SIGNATURE)
                .algorithm(JWSAlgorithm.RS256)
                .build();

        Map<String, Object> jwkSet = new JWKSet(rsaKey).toJSONObject();

        return ResponseEntity.ok()
                .cacheControl(CacheControl.maxAge(600, TimeUnit.SECONDS).cachePublic())
                .body(jwkSet);
    }
}

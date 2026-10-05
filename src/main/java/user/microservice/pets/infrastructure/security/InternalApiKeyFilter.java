package user.microservice.pets.infrastructure.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * Protege /internal/** (CONTRATOS_COMPARTIDOS.md §2): esas rutas no aceptan JWT de usuario,
 * solo el header X-Internal-Api-Key comparado en tiempo constante. Sin la llave correcta, 401.
 */
@Component
public class InternalApiKeyFilter extends OncePerRequestFilter {

    private static final String HEADER_NAME = "X-Internal-Api-Key";

    private final byte[] expectedKeyBytes;

    public InternalApiKeyFilter(@Value("${internal.api-key}") String expectedKey) {
        this.expectedKeyBytes = expectedKey.getBytes(StandardCharsets.UTF_8);
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith("/internal/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String providedKey = request.getHeader(HEADER_NAME);

        if (providedKey == null
                || !MessageDigest.isEqual(providedKey.getBytes(StandardCharsets.UTF_8), expectedKeyBytes)) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json");
            response.setCharacterEncoding("UTF-8");
            response.getWriter().write("{\"error\": \"Invalid or missing X-Internal-Api-Key\"}");
            return;
        }

        filterChain.doFilter(request, response);
    }
}

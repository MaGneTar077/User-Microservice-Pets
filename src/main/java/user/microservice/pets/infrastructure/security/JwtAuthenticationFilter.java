package user.microservice.pets.infrastructure.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.MalformedJwtException;
import io.jsonwebtoken.security.SignatureException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import user.microservice.pets.application.services.LogoutService;

import java.io.IOException;
import java.util.Collections;

@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtUtil jwtUtil;
    private final LogoutService logoutService;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();

        // /auth/context/** SI requiere autenticacion (token de usuario valido), a diferencia
        // del resto de /auth/** (login/refresh/logout son publicos o manejan su propio token).
        if (path.startsWith("/auth/context/")) {
            return false;
        }

        return path.startsWith("/auth/") ||
                path.startsWith("/user/register") ||
                path.equals("/error") ||
                path.equals("/.well-known/jwks.json") ||
                path.startsWith("/internal/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {

        String authHeader = request.getHeader("Authorization");

        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String token = authHeader.substring(7).trim();

            try {
                Claims claims = jwtUtil.validateToken(token);

                if (logoutService.isTokenInvalid(token)) {
                    log.warn("Attempt to use invalidated token");
                    sendErrorResponse(response, HttpServletResponse.SC_UNAUTHORIZED, "Token has been invalidated");
                    return;
                }

                // El principal se identifica por el claim "id" (presente en tokens viejos HS256
                // y nuevos RS256), no por "sub": en los tokens nuevos sub pasa a ser el UUID del
                // usuario segun el contrato compartido, pero en los viejos sub era el email.
                String userId = claims.get("id", String.class);
                if (userId != null && SecurityContextHolder.getContext().getAuthentication() == null) {
                    UsernamePasswordAuthenticationToken authentication =
                            new UsernamePasswordAuthenticationToken(userId, null, Collections.emptyList());
                    authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                    SecurityContextHolder.getContext().setAuthentication(authentication);
                }
            } catch (ExpiredJwtException e) {
                sendErrorResponse(response, HttpServletResponse.SC_UNAUTHORIZED, "Token expired");
                return;
            } catch (SignatureException e) {
                sendErrorResponse(response, HttpServletResponse.SC_UNAUTHORIZED, "Invalid token signature");
                return;
            } catch (MalformedJwtException e) {
                sendErrorResponse(response, HttpServletResponse.SC_UNAUTHORIZED, "Malformed token");
                return;
            } catch (Exception e) {
                // Incluye fallos de Redis: si no se puede comprobar, no se deja pasar
                log.error("Error validando JWT: {}", e.getMessage());
                sendErrorResponse(response, HttpServletResponse.SC_UNAUTHORIZED, "Invalid token");
                return;
            }
        }

        filterChain.doFilter(request, response);
    }

    private void sendErrorResponse(HttpServletResponse response, int status, String message) throws IOException {
        response.setStatus(status);
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write("{\"error\": \"" + message + "\"}");
    }
}
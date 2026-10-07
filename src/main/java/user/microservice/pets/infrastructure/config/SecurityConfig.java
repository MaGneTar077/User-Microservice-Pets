package user.microservice.pets.infrastructure.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import user.microservice.pets.infrastructure.security.InternalApiKeyFilter;
import user.microservice.pets.infrastructure.security.JwtAuthenticationFilter;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final InternalApiKeyFilter internalApiKeyFilter;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)

                .authorizeHttpRequests(auth -> auth
                        // Mas especifico primero: /auth/context/** SI exige autenticacion,
                        // aunque caiga bajo el patron /auth/** que es publico mas abajo.
                        .requestMatchers("/auth/context/**").authenticated()
                        .requestMatchers(
                                "/auth/**",
                                "/user/register",
                                "/api/**",
                                "/actuator/**",
                                "/error",
                                "/.well-known/jwks.json",
                                // /internal/** no usa JWT de usuario: lo protege InternalApiKeyFilter
                                // (X-Internal-Api-Key), no Spring Security.
                                "/internal/**"
                        ).permitAll()
                        .anyRequest().authenticated()
                )

                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS)
                )

                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(internalApiKeyFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}
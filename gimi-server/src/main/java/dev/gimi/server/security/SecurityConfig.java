package dev.gimi.server.security;

import java.util.Arrays;
import java.util.List;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

/**
 * Spring Security configuration for the gimi API server.
 * Stateless JWT-based authentication with role-based access control.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    private final JwtAuthFilter jwtAuthFilter;

    public SecurityConfig(JwtAuthFilter jwtAuthFilter) {
        this.jwtAuthFilter = jwtAuthFilter;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        return http
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        // Public endpoints
                        .requestMatchers("/api/auth/login").permitAll()
                        .requestMatchers("/api/auth/register").permitAll()
                        .requestMatchers("/actuator/health").permitAll()
                        .requestMatchers("/actuator/prometheus").permitAll()
                        .requestMatchers("/webhook").permitAll()
                        .requestMatchers("/error").permitAll()

                        // Admin-only endpoints
                        .requestMatchers("/api/admin/**").hasRole("ADMIN")

                        // Operator+ endpoints (ADMIN or OPERATOR)
                        .requestMatchers(HttpMethod.POST, "/api/runs/**").hasAnyRole("ADMIN", "OPERATOR")
                        .requestMatchers(HttpMethod.POST, "/api/approvals/**").hasAnyRole("ADMIN", "OPERATOR")

                        // Developer+ endpoints (ADMIN, OPERATOR, or DEVELOPER)
                        .requestMatchers(HttpMethod.GET, "/api/pipelines/**").hasAnyRole("ADMIN", "OPERATOR", "DEVELOPER")
                        .requestMatchers(HttpMethod.POST, "/api/pipelines/**").hasAnyRole("ADMIN", "OPERATOR", "DEVELOPER")
                        .requestMatchers(HttpMethod.GET, "/api/runs/**").hasAnyRole("ADMIN", "OPERATOR", "DEVELOPER")
                        .requestMatchers(HttpMethod.GET, "/api/logs/**").hasAnyRole("ADMIN", "OPERATOR", "DEVELOPER")
                        .requestMatchers(HttpMethod.GET, "/api/artifacts/**").hasAnyRole("ADMIN", "OPERATOR", "DEVELOPER")
                        .requestMatchers(HttpMethod.GET, "/api/approvals/**").hasAnyRole("ADMIN", "OPERATOR", "DEVELOPER")
                        .requestMatchers(HttpMethod.GET, "/api/workers/**").hasAnyRole("ADMIN", "OPERATOR")
                        .requestMatchers(HttpMethod.POST, "/api/workers/**").hasAnyRole("ADMIN", "OPERATOR")
                        .requestMatchers(HttpMethod.DELETE, "/api/workers/**").hasAnyRole("ADMIN", "OPERATOR")

                        // Authenticated endpoints for all remaining APIs
                        .requestMatchers("/api/connectors/**").authenticated()
                        .requestMatchers("/api/slo/**").authenticated()
                        .requestMatchers("/api/policies/**").authenticated()
                        .requestMatchers("/api/secrets/**").authenticated()
                        .requestMatchers("/api/sso/**").authenticated()
                        .requestMatchers("/api/feature-flags/**").authenticated()
                        .requestMatchers("/api/audit/**").authenticated()
                        .requestMatchers("/api/analytics/**").authenticated()
                        .requestMatchers("/api/tenants/**").authenticated()
                        .requestMatchers("/api/rbac/**").authenticated()
                        .requestMatchers("/api/gitops/**").authenticated()
                        .requestMatchers("/api/chaos/**").authenticated()
                        .requestMatchers("/api/verification/**").authenticated()
                        .requestMatchers("/api/costs/**").authenticated()
                        .requestMatchers("/api/security/**").authenticated()

                        // Everything else requires authentication
                        .anyRequest().authenticated()
                )
                .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class)
                .build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(List.of("http://localhost:3000", "http://localhost:5173"));
        configuration.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("*"));
        configuration.setAllowCredentials(true);
        configuration.setMaxAge(3600L);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }
}

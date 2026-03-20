package dev.gimi.server.security;

import dev.gimi.server.config.ServerConfig;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;

/**
 * Authenticates worker registration and heartbeat requests using a shared secret.
 * Workers must present X-Worker-Token header derived from the JWT secret.
 */
@Component
public class WorkerAuthFilter extends OncePerRequestFilter {

    private static final String WORKER_TOKEN_HEADER = "X-Worker-Token";
    private static final String HMAC_SHA256 = "HmacSHA256";

    private final String expectedToken;

    public WorkerAuthFilter(ServerConfig serverConfig) {
        this.expectedToken = deriveWorkerToken(serverConfig.getJwtSecret());
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return !(path.startsWith("/api/workers/"));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        // Skip if request already has a valid JWT (admin managing workers via API)
        if (request.getHeader("Authorization") != null) {
            filterChain.doFilter(request, response);
            return;
        }

        String token = request.getHeader(WORKER_TOKEN_HEADER);
        if (token == null || !token.equals(expectedToken)) {
            response.setStatus(HttpStatus.UNAUTHORIZED.value());
            response.setContentType("application/json");
            response.getWriter().write("{\"error\":\"Invalid or missing worker token. Set X-Worker-Token header.\"}");
            return;
        }

        filterChain.doFilter(request, response);
    }

    /**
     * Derives a deterministic worker token from the JWT secret using HMAC-SHA256.
     */
    static String deriveWorkerToken(String jwtSecret) {
        try {
            Mac mac = Mac.getInstance(HMAC_SHA256);
            mac.init(new SecretKeySpec(jwtSecret.getBytes(StandardCharsets.UTF_8), HMAC_SHA256));
            byte[] hash = mac.doFinal("gimi-worker-auth".getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to derive worker token", e);
        }
    }
}

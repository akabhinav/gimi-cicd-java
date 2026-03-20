package dev.gimi.server.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Token-bucket rate limiter for sensitive endpoints.
 * Limits per client IP to prevent brute-force and DoS attacks.
 */
@Component
public class RateLimitFilter extends OncePerRequestFilter {

    private static final int AUTH_REQUESTS_PER_MINUTE = 20;
    private static final int WEBHOOK_REQUESTS_PER_MINUTE = 120;
    private static final int REGISTER_REQUESTS_PER_MINUTE = 5;
    private static final long WINDOW_MS = 60_000L;

    private final Map<String, RateBucket> buckets = new ConcurrentHashMap<>();

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        String path = request.getRequestURI();
        int limit = resolveLimit(path);

        if (limit > 0) {
            String clientIp = getClientIp(request);
            String key = clientIp + ":" + normalizePath(path);

            RateBucket bucket = buckets.computeIfAbsent(key, k -> new RateBucket(limit));

            if (!bucket.tryConsume()) {
                response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
                response.setContentType("application/json");
                response.getWriter().write("{\"error\":\"Rate limit exceeded. Try again later.\"}");
                return;
            }
        }

        filterChain.doFilter(request, response);
    }

    private int resolveLimit(String path) {
        if (path.equals("/api/auth/login")) {
            return AUTH_REQUESTS_PER_MINUTE;
        }
        if (path.equals("/api/auth/register")) {
            return REGISTER_REQUESTS_PER_MINUTE;
        }
        if (path.equals("/webhook")) {
            return WEBHOOK_REQUESTS_PER_MINUTE;
        }
        return 0;
    }

    private String normalizePath(String path) {
        if (path.startsWith("/api/auth/")) return "/api/auth";
        if (path.startsWith("/webhook")) return "/webhook";
        return path;
    }

    private String getClientIp(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isBlank()) {
            return xForwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    /**
     * Simple sliding-window rate bucket.
     */
    private static class RateBucket {
        private final int maxRequests;
        private final AtomicInteger count = new AtomicInteger(0);
        private final AtomicLong windowStart = new AtomicLong(System.currentTimeMillis());

        RateBucket(int maxRequests) {
            this.maxRequests = maxRequests;
        }

        boolean tryConsume() {
            long now = System.currentTimeMillis();
            long start = windowStart.get();

            if (now - start > WINDOW_MS) {
                // Reset window
                windowStart.set(now);
                count.set(1);
                return true;
            }

            return count.incrementAndGet() <= maxRequests;
        }
    }
}

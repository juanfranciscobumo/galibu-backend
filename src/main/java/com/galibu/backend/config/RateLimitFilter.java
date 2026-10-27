package com.galibu.backend.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Rate limiter simple para endpoints de autenticación.
 * Limita a 5 intentos por IP cada 60 segundos en /api/auth/login y /api/auth/register.
 */
@Component
public class RateLimitFilter extends OncePerRequestFilter {

    private static final int MAX_ATTEMPTS = 5;
    private static final long WINDOW_MS = 60_000L;

    private final ConcurrentHashMap<String, List<Long>> attempts = new ConcurrentHashMap<>();

    @Override
    protected void doFilterInternal(
        HttpServletRequest request,
        HttpServletResponse response,
        FilterChain filterChain
    ) throws ServletException, IOException {
        String path = request.getRequestURI();
        if (!path.equals("/api/auth/login") && !path.equals("/api/auth/register") && !path.equals("/api/auth/google")) {
            filterChain.doFilter(request, response);
            return;
        }

        String ip = getClientIp(request);
        long now = System.currentTimeMillis();

        List<Long> timestamps = attempts.computeIfAbsent(ip, k -> new ArrayList<>());
        synchronized (timestamps) {
            // Remove expired entries
            timestamps.removeIf(ts -> now - ts > WINDOW_MS);

            if (timestamps.size() >= MAX_ATTEMPTS) {
                long oldest = timestamps.get(0);
                long retryAfter = (WINDOW_MS - (now - oldest)) / 1000;
                response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
                response.setContentType("application/json;charset=UTF-8");
                response.setCharacterEncoding("UTF-8");
                response.setHeader("Retry-After", String.valueOf(retryAfter));
                response.getWriter().write(
                    "{\"status\":429,\"error\":\"Too Many Requests\"," +
                    "\"message\":\"Demasiados intentos. Intenta de nuevo en " + retryAfter + " segundos.\"}");
                return;
            }

            timestamps.add(now);
        }

        filterChain.doFilter(request, response);
    }

    private String getClientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        String realIp = request.getHeader("X-Real-IP");
        if (realIp != null && !realIp.isBlank()) {
            return realIp.trim();
        }
        return request.getRemoteAddr();
    }
}
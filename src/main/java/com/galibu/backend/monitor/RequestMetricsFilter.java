package com.galibu.backend.monitor;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.galibu.backend.security.JwtService;
import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Pattern;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingRequestWrapper;
import org.springframework.web.util.ContentCachingResponseWrapper;

/**
 * Mide la duración y el código de estado de cada petición HTTP y guarda una
 * traza correlacionada por {@code X-Request-ID}. Se ejecuta antes de la cadena
 * de seguridad para contar también los 401/403 y para poder capturar el cuerpo
 * de la petición antes de que Spring lo consuma.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 20)
public class RequestMetricsFilter extends OncePerRequestFilter {

    private static final Pattern VALID_REQUEST_ID = Pattern.compile("[A-Za-z0-9._:-]{1,128}");
    private static final int MAX_CAPTURE_BYTES = 512 * 1024;

    private final RequestMetrics metrics;
    private final RequestTraceStore traceStore;
    private final UsageLedger usage;
    private final JwtService jwtService;
    private final ObjectMapper objectMapper;

    public RequestMetricsFilter(
        RequestMetrics metrics,
        RequestTraceStore traceStore,
        UsageLedger usage,
        JwtService jwtService,
        ObjectMapper objectMapper
    ) {
        this.metrics = metrics;
        this.traceStore = traceStore;
        this.usage = usage;
        this.jwtService = jwtService;
        this.objectMapper = objectMapper;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String uri = request.getRequestURI();
        return uri.startsWith("/api/monitor") || uri.startsWith("/monitor")
            || uri.equals("/api/front-logs") || uri.startsWith("/api/front-logs/")
            || uri.startsWith("/actuator") || uri.equals("/favicon.ico");
    }

    @Override
    protected void doFilterInternal(
        HttpServletRequest request,
        HttpServletResponse response,
        FilterChain filterChain
    ) throws ServletException, IOException {
        long start = System.nanoTime();
        String requestId = resolveRequestId(request);
        String userId = resolveUserId(request);
        String clientApp = header(request, "X-Client-App");
        String clientPlatform = header(request, "X-Client-Platform");
        String clientVersion = header(request, "X-Client-Version");
        String clientDevice = header(request, "X-Client-Device");
        String userAgent = request.getHeader("User-Agent");

        request.setAttribute("requestId", requestId);
        response.setHeader("X-Request-ID", requestId);
        MDC.put("requestId", requestId);
        if (userId != null && !userId.isBlank()) {
            MDC.put("userId", userId);
        }

        ContentCachingRequestWrapper wrappedRequest = new ContentCachingRequestWrapper(request);
        ContentCachingResponseWrapper wrappedResponse = new ContentCachingResponseWrapper(response);
        int status = 500;
        String detail = null;
        try {
            filterChain.doFilter(wrappedRequest, wrappedResponse);
            status = wrappedResponse.getStatus();
        } catch (ServletException | IOException | RuntimeException ex) {
            status = 500;
            detail = ex.getClass().getSimpleName() + ": " + ex.getMessage();
            throw ex;
        } finally {
            byte[] responseBody = wrappedResponse.getContentAsByteArray();
            if (status >= 400 && detail == null) {
                detail = extractDetail(responseBody, explicitCharset(wrappedResponse));
            }
            long durationMs = (System.nanoTime() - start) / 1_000_000;
            String path = request.getRequestURI();
            String client = clientIp(request);

            metrics.record(request.getMethod(), path, status, durationMs, client, detail);
            usage.record(status, durationMs);

            byte[] requestBody = requestBody(wrappedRequest);
            traceStore.record(new RequestTraceStore.CallData(
                requestId,
                System.currentTimeMillis(),
                request.getMethod(),
                path,
                status,
                durationMs,
                client,
                userId,
                clientApp,
                clientPlatform,
                clientVersion,
                clientDevice,
                userAgent,
                request.getContentType(),
                wrappedResponse.getContentType(),
                requestBody,
                responseBody
            ));

            wrappedResponse.copyBodyToResponse();
            MDC.remove("requestId");
            MDC.remove("userId");
        }
    }

    private static byte[] requestBody(ContentCachingRequestWrapper request) {
        String contentType = request.getContentType();
        if (contentType == null) {
            return new byte[0];
        }
        String lower = contentType.toLowerCase(Locale.ROOT);
        if (lower.contains("multipart/") || lower.startsWith("image/") || lower.contains("octet-stream")) {
            return new byte[0];
        }
        byte[] body = request.getContentAsByteArray();
        if (body == null || body.length == 0 || body.length > MAX_CAPTURE_BYTES) {
            return new byte[0];
        }
        return body;
    }

    private static String resolveRequestId(HttpServletRequest request) {
        String value = firstHeader(request, "X-Request-ID", "X-Correlation-ID", "X-Transaction-ID");
        if (value != null) {
            String candidate = value.trim();
            if (VALID_REQUEST_ID.matcher(candidate).matches()) {
                return candidate;
            }
        }
        return "req_" + UUID.randomUUID().toString().replace("-", "");
    }

    private String resolveUserId(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (header == null || !header.startsWith("Bearer ")) {
            return null;
        }
        try {
            Claims claims = jwtService.parseValidAccessToken(header.substring(7));
            return claims.getSubject();
        } catch (Exception ignored) {
            return null;
        }
    }

    private static String firstHeader(HttpServletRequest request, String... names) {
        for (String name : names) {
            String value = request.getHeader(name);
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }

    private static String header(HttpServletRequest request, String name) {
        String value = request.getHeader(name);
        return value == null ? null : value.trim();
    }

    private String extractDetail(byte[] body, Charset charset) {
        if (body == null || body.length == 0) {
            return null;
        }
        String text = new String(body, charset).trim();
        if (text.isEmpty()) {
            return null;
        }
        try {
            JsonNode node = objectMapper.readTree(text);
            JsonNode message = node.get("message");
            if (message != null && !message.isNull() && !message.asText().isBlank()) {
                return message.asText();
            }
            JsonNode error = node.get("error");
            if (error != null && !error.isNull() && !error.asText().isBlank()) {
                return error.asText();
            }
        } catch (Exception ignored) {
            // La respuesta no es JSON: se usa el texto tal cual.
        }
        return text;
    }

    private static Charset explicitCharset(HttpServletResponse response) {
        String contentType = response.getContentType();
        if (contentType != null) {
            String lower = contentType.toLowerCase(Locale.ROOT);
            int index = lower.indexOf("charset=");
            if (index >= 0) {
                String name = contentType.substring(index + 8).split(";")[0].trim();
                try {
                    return Charset.forName(name);
                } catch (Exception ignored) {
                    // Charset inválido: se usa UTF-8.
                }
            }
        }
        return StandardCharsets.UTF_8;
    }

    private static String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}

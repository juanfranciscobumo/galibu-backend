package com.galibu.backend.monitor;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/**
 * Guarda en memoria las últimas transacciones HTTP agrupadas por
 * {@code X-Request-ID}. Una transacción puede contener varias llamadas a la
 * API (por ejemplo crear una solicitud + refrescar el job + refrescar ofertas)
 * y el dashboard las muestra juntas con request y response de cada una.
 */
@Component
public class RequestTraceStore {

    private static final int MAX_TRACES = 300;
    private static final int MAX_CALLS_PER_TRACE = 40;
    private static final int MAX_BODY_CHARS = 8000;
    private static final int MAX_LONG_TEXT = 1200;

    private static final Pattern FALLBACK_SECRET = Pattern.compile(
        "(?i)(\"(?:password|token|accessToken|refreshToken|idToken|secret|authorization)\"\\s*:\\s*)\"[^\"]*\"");

    private final ObjectMapper objectMapper;
    private final LinkedHashMap<String, Trace> traces = new LinkedHashMap<>();
    private final ArrayDeque<String> order = new ArrayDeque<>();
    private long totalCalls;

    public RequestTraceStore(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public synchronized void record(CallData data) {
        if (data == null || data.requestId() == null || data.requestId().isBlank()) {
            return;
        }
        long callEnd = data.at() + Math.max(0, data.ms());
        Trace trace = traces.get(data.requestId());
        if (trace == null) {
            trace = new Trace(data.requestId(), data.at(), callEnd);
            trace.app = data.app();
            trace.platform = data.platform();
            trace.version = data.version();
            trace.userId = data.userId();
            trace.device = data.device();
            traces.put(data.requestId(), trace);
            order.addLast(data.requestId());
            trimTraces();
        } else {
            if (isBlank(trace.app)) trace.app = data.app();
            if (isBlank(trace.platform)) trace.platform = data.platform();
            if (isBlank(trace.version)) trace.version = data.version();
            if (isBlank(trace.userId)) trace.userId = data.userId();
            if (isBlank(trace.device)) trace.device = data.device();
        }
        trace.firstAt = Math.min(trace.firstAt, data.at());
        trace.lastAt = Math.max(trace.lastAt, callEnd);
        trace.calls.add(new Call(
            data.at(),
            data.method(),
            data.path(),
            data.status(),
            data.ms(),
            data.client(),
            data.userAgent(),
            data.requestContentType(),
            data.responseContentType(),
            sanitize(data.requestBody(), data.requestContentType()),
            sanitize(data.responseBody(), data.responseContentType())
        ));
        while (trace.calls.size() > MAX_CALLS_PER_TRACE) {
            trace.calls.remove(0);
        }
        totalCalls++;
    }

    /** Búsqueda por id, ruta, app o usuario. Vacío devuelve las más recientes. */
    public synchronized List<Map<String, Object>> search(String query, int limit) {
        String needle = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        List<Trace> matches = new ArrayList<>();
        for (Trace trace : traces.values()) {
            if (matches(trace, needle)) {
                matches.add(trace);
            }
        }
        matches.sort(Comparator.comparingLong((Trace t) -> t.lastAt).reversed());
        List<Map<String, Object>> out = new ArrayList<>();
        int max = Math.max(1, Math.min(limit, 100));
        for (int i = 0; i < matches.size() && out.size() < max; i++) {
            out.add(matches.get(i).summary());
        }
        return out;
    }

    public synchronized Map<String, Object> detail(String requestId) {
        Trace trace = traces.get(requestId);
        return trace == null ? null : trace.detail();
    }

    public synchronized void clear() {
        traces.clear();
        order.clear();
        totalCalls = 0;
    }

    public synchronized Map<String, Object> summary() {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("transactions", traces.size());
        out.put("calls", totalCalls);
        out.put("capacity", MAX_TRACES);
        return out;
    }

    private boolean matches(Trace trace, String needle) {
        if (needle.isEmpty()) {
            return true;
        }
        if (contains(trace.requestId, needle) || contains(trace.app, needle)
            || contains(trace.userId, needle) || contains(trace.platform, needle)) {
            return true;
        }
        for (Call call : trace.calls) {
            if (contains(call.path, needle) || contains(call.method, needle)) {
                return true;
            }
        }
        return false;
    }

    private void trimTraces() {
        while (traces.size() > MAX_TRACES && !order.isEmpty()) {
            String oldest = order.removeFirst();
            traces.remove(oldest);
        }
    }

    private String sanitize(byte[] body, String contentType) {
        if (body == null || body.length == 0) {
            return null;
        }
        String type = contentType == null ? "" : contentType.toLowerCase(Locale.ROOT);
        if (type.startsWith("image/") || type.startsWith("audio/") || type.startsWith("video/")
            || type.contains("octet-stream") || type.contains("pdf") || type.contains("multipart/")) {
            return "[contenido binario · " + body.length + " bytes]";
        }
        if (body.length > MAX_BODY_CHARS * 4) {
            return "[cuerpo omitido · " + body.length + " bytes]";
        }
        String text = new String(body, StandardCharsets.UTF_8);
        if (text.isBlank()) {
            return null;
        }
        if (type.contains("json") || text.stripLeading().startsWith("{") || text.stripLeading().startsWith("[")) {
            try {
                JsonNode node = objectMapper.readTree(text);
                redact(node);
                String pretty = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(node);
                return truncate(pretty);
            } catch (Exception ignored) {
                // Si no es JSON válido, se sanea como texto.
            }
        }
        text = FALLBACK_SECRET.matcher(text).replaceAll("$1\"***\"");
        return truncate(text);
    }

    private void redact(JsonNode node) {
        if (node == null) {
            return;
        }
        if (node.isObject()) {
            ObjectNode object = (ObjectNode) node;
            Iterator<Map.Entry<String, JsonNode>> fields = object.fields();
            List<String> keys = new ArrayList<>();
            while (fields.hasNext()) {
                keys.add(fields.next().getKey());
            }
            for (String key : keys) {
                JsonNode value = object.get(key);
                if (isSensitive(key)) {
                    object.put(key, "***");
                } else if (value.isTextual()) {
                    object.put(key, sanitizeText(value.asText()));
                } else {
                    redact(value);
                }
            }
        } else if (node.isArray()) {
            ArrayNode array = (ArrayNode) node;
            for (int i = 0; i < array.size(); i++) {
                JsonNode child = array.get(i);
                if (child.isTextual()) {
                    array.set(i, objectMapper.getNodeFactory().textNode(sanitizeText(child.asText())));
                } else {
                    redact(child);
                }
            }
        }
    }

    private static String sanitizeText(String value) {
        if (value == null) {
            return null;
        }
        if (value.startsWith("data:") && value.contains("base64,")) {
            int comma = value.indexOf(',');
            return value.substring(0, Math.min(comma + 1, value.length())) + "[base64 omitido]";
        }
        if (value.length() > MAX_LONG_TEXT) {
            return value.substring(0, MAX_LONG_TEXT) + "… [texto omitido]";
        }
        return value;
    }

    private static boolean isSensitive(String key) {
        String lower = key.toLowerCase(Locale.ROOT);
        return lower.contains("password") || lower.contains("token") || lower.contains("secret")
            || lower.contains("authorization") || lower.equals("otp") || lower.equals("pin")
            || lower.contains("cvv") || lower.contains("cardnumber");
    }

    private static String truncate(String text) {
        if (text == null) {
            return null;
        }
        return text.length() <= MAX_BODY_CHARS ? text
            : text.substring(0, MAX_BODY_CHARS - 1) + "…";
    }

    private static boolean contains(String value, String needle) {
        return value != null && value.toLowerCase(Locale.ROOT).contains(needle);
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    public record CallData(
        String requestId,
        long at,
        String method,
        String path,
        int status,
        long ms,
        String client,
        String userId,
        String app,
        String platform,
        String version,
        String device,
        String userAgent,
        String requestContentType,
        String responseContentType,
        byte[] requestBody,
        byte[] responseBody
    ) {
    }

    private static final class Trace {
        private final String requestId;
        private long firstAt;
        private long lastAt;
        private String app;
        private String platform;
        private String version;
        private String userId;
        private String device;
        private final List<Call> calls = new ArrayList<>();

        private Trace(String requestId, long firstAt, long lastAt) {
            this.requestId = requestId;
            this.firstAt = firstAt;
            this.lastAt = lastAt;
        }

        private Map<String, Object> summary() {
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("requestId", requestId);
            out.put("firstAt", firstAt);
            out.put("lastAt", lastAt);
            out.put("durationMs", Math.max(0, lastAt - firstAt));
            out.put("app", app);
            out.put("platform", platform);
            out.put("version", version);
            out.put("userId", userId);
            out.put("device", device);
            out.put("calls", calls.size());
            out.put("errors", calls.stream().filter(call -> call.status >= 400).count());
            out.put("lastStatus", calls.isEmpty() ? 0 : calls.get(calls.size() - 1).status);
            return out;
        }

        private Map<String, Object> detail() {
            Map<String, Object> out = summary();
            List<Map<String, Object>> items = new ArrayList<>();
            for (Call call : calls) {
                items.add(call.toMap());
            }
            out.put("calls", items);
            return out;
        }
    }

    private static final class Call {
        private final long at;
        private final String method;
        private final String path;
        private final int status;
        private final long ms;
        private final String client;
        private final String userAgent;
        private final String requestContentType;
        private final String responseContentType;
        private final String requestBody;
        private final String responseBody;

        private Call(
            long at,
            String method,
            String path,
            int status,
            long ms,
            String client,
            String userAgent,
            String requestContentType,
            String responseContentType,
            String requestBody,
            String responseBody
        ) {
            this.at = at;
            this.method = method;
            this.path = path;
            this.status = status;
            this.ms = ms;
            this.client = client;
            this.userAgent = userAgent;
            this.requestContentType = requestContentType;
            this.responseContentType = responseContentType;
            this.requestBody = requestBody;
            this.responseBody = responseBody;
        }

        private Map<String, Object> toMap() {
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("at", at);
            out.put("method", method);
            out.put("path", path);
            out.put("status", status);
            out.put("ms", ms);
            out.put("client", client);
            out.put("userAgent", userAgent);
            out.put("requestContentType", requestContentType);
            out.put("responseContentType", responseContentType);
            out.put("requestBody", requestBody);
            out.put("responseBody", responseBody);
            out.put("requestSize", requestBody == null ? 0 : requestBody.length());
            out.put("responseSize", responseBody == null ? 0 : responseBody.length());
            return out;
        }
    }
}

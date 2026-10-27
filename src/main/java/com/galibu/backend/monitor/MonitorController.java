package com.galibu.backend.monitor;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** API que alimenta el dashboard de monitoreo. */
@RestController
@RequestMapping("/api/monitor")
public class MonitorController {

    private final RequestMetrics metrics;
    private final JvmMetrics jvm;
    private final DatabaseMetrics database;
    private final LogCapture logCapture;
    private final EndpointCatalog catalog;
    private final RequestTraceStore traces;
    private final FrontLogStore frontLogs;
    private final AppStatsService appStats;

    public MonitorController(
        RequestMetrics metrics,
        JvmMetrics jvm,
        DatabaseMetrics database,
        LogCapture logCapture,
        EndpointCatalog catalog,
        RequestTraceStore traces,
        FrontLogStore frontLogs,
        AppStatsService appStats
    ) {
        this.metrics = metrics;
        this.jvm = jvm;
        this.database = database;
        this.logCapture = logCapture;
        this.catalog = catalog;
        this.traces = traces;
        this.frontLogs = frontLogs;
        this.appStats = appStats;
    }

    @GetMapping("/data")
    public Map<String, Object> data() {
        Map<String, Object> snapshot = metrics.snapshot();
        mergeCatalog(snapshot);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("serverTime", System.currentTimeMillis());
        out.put("metrics", snapshot);
        out.put("jvm", jvm.snapshot());
        out.put("db", database.snapshot());
        out.put("logs", logCapture.summary());
        out.put("traces", traces.summary());
        return out;
    }

    /** Lista/resumen de transacciones. Acepta búsqueda por request id o texto. */
    @GetMapping("/traces")
    public Map<String, Object> traces(
        @RequestParam(value = "q", required = false) String query,
        @RequestParam(value = "requestId", required = false) String requestId,
        @RequestParam(defaultValue = "40") int limit
    ) {
        String needle = requestId != null && !requestId.isBlank() ? requestId : query;
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("query", needle == null ? "" : needle);
        out.put("items", traces.search(needle, Math.max(1, Math.min(limit, 100))));
        out.put("summary", traces.summary());
        return out;
    }

    @GetMapping("/traces/{requestId}")
    public ResponseEntity<Map<String, Object>> trace(@PathVariable String requestId) {
        Map<String, Object> detail = traces.detail(requestId);
        return detail == null ? ResponseEntity.notFound().build() : ResponseEntity.ok(detail);
    }

    @GetMapping("/front-logs")
    public Map<String, Object> frontLogs(
        @RequestParam(defaultValue = "ALL") String level,
        @RequestParam(defaultValue = "ALL") String app,
        @RequestParam(value = "q", required = false) String query,
        @RequestParam(defaultValue = "300") int limit
    ) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("items", frontLogs.recent(level, app, query, Math.max(1, Math.min(limit, 500))));
        out.put("summary", frontLogs.summary());
        out.put("status", frontLogs.status());
        return out;
    }

    @GetMapping("/app-stats")
    public Map<String, Object> appStats() {
        return appStats.snapshot();
    }

    /**
     * Agrega al listado de métricas las rutas registradas que todavía no han
     * recibido tráfico, para poder ver la cobertura completa de la API.
     */
    @SuppressWarnings("unchecked")
    private void mergeCatalog(Map<String, Object> snapshot) {
        List<Map<String, Object>> used = (List<Map<String, Object>>) snapshot.get("endpoints");
        Map<String, Map<String, Object>> pending = new LinkedHashMap<>();
        for (Map<String, Object> row : used) {
            pending.put(row.get("method") + " " + row.get("path"), row);
        }
        List<Map<String, Object>> all = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (EndpointCatalog.Endpoint endpoint : catalog.endpoints()) {
            String key = endpoint.key();
            if (!seen.add(key)) {
                continue;
            }
            Map<String, Object> row = pending.remove(key);
            if (row == null) {
                row = emptyEndpoint(endpoint);
            }
            row.put("registered", true);
            all.add(row);
        }
        for (Map<String, Object> row : pending.values()) {
            row.put("registered", false);
            all.add(row);
        }
        snapshot.put("endpoints", all);
        snapshot.put("catalogSize", seen.size());
    }

    private static Map<String, Object> emptyEndpoint(EndpointCatalog.Endpoint endpoint) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("method", endpoint.method());
        row.put("path", endpoint.path());
        row.put("count", 0L);
        row.put("errors", 0L);
        row.put("rps", 0.0);
        row.put("avgMs", 0.0);
        row.put("p95Ms", 0L);
        row.put("maxMs", 0L);
        row.put("lastStatus", 0);
        row.put("lastMs", 0L);
        row.put("lastAt", 0L);
        return row;
    }

    @GetMapping("/logs")
    public List<Map<String, Object>> logs(
        @RequestParam(defaultValue = "ALL") String level,
        @RequestParam(defaultValue = "250") int limit
    ) {
        return logCapture.recent(level, Math.max(1, Math.min(limit, 600)));
    }

    @PostMapping("/reset")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void reset() {
        metrics.reset();
        traces.clear();
    }
}

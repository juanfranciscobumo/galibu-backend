package com.galibu.backend.monitor;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * Evalúa el estado del backend contra umbrales simples y dispara alertas
 * persistentes cuando una condición cambia de estado. También se encarga de
 * tomar la foto periódica de métricas (histórico) y de limpiar datos viejos.
 */
@Service
public class AlertService {

    /** Ventana móvil usada para las tasas de error y latencia. */
    private static final int WINDOW_MINUTES = 5;

    private final RequestMetrics metrics;
    private final JvmMetrics jvm;
    private final DatabaseMetrics database;
    private final FrontLogStore frontLogs;
    private final MetricsHistoryStore history;
    private final JdbcTemplate jdbc;
    private final AppConfigService config;

    /** Severidad actualmente abierta por alerta (null si está resuelta). */
    private final Map<String, String> openSeverity = new ConcurrentHashMap<>();
    private final Map<String, Map<String, Object>> openAlerts = new ConcurrentHashMap<>();
    private volatile boolean stateLoaded = false;
    private volatile long lastEvaluatedAt = 0;

    public AlertService(
        RequestMetrics metrics,
        JvmMetrics jvm,
        DatabaseMetrics database,
        FrontLogStore frontLogs,
        MetricsHistoryStore history,
        JdbcTemplate jdbc,
        AppConfigService config
    ) {
        this.metrics = metrics;
        this.jvm = jvm;
        this.database = database;
        this.frontLogs = frontLogs;
        this.history = history;
        this.jdbc = jdbc;
        this.config = config;
    }

    public Map<String, Object> current() {
        MetricsSnapshot snapshot = capture();
        List<Map<String, Object>> active = evaluate(snapshot);
        return buildResult(snapshot, active, System.currentTimeMillis());
    }

    /** Evaluación periódica: toma la foto, la persiste y actualiza las alertas. */
    @Scheduled(
        initialDelayString = "${app.monitor.alerts.initial-delay-ms:30000}",
        fixedDelayString = "${app.monitor.alerts.interval-ms:300000}"
    )
    public void evaluateAndRecord() {
        MetricsSnapshot snapshot = capture();
        List<Map<String, Object>> active = evaluate(snapshot);
        try {
            history.record(snapshot);
        } catch (Exception ignored) {
            // No interrumpir la evaluación si la base falla.
        }
        persistTransitions(active);
        try {
            history.prune(30);
            jdbc.update(
                "delete from alert_events where status = 'RESOLVED' and created_at < ?",
                System.currentTimeMillis() - 90L * 86_400_000L);
        } catch (Exception ignored) {
        }
        lastEvaluatedAt = System.currentTimeMillis();
    }

    public List<Map<String, Object>> history(int limit) {
        int cap = Math.max(1, Math.min(limit, 500));
        try {
            return jdbc.queryForList(
                "select id, alert_key, severity, status, title, detail, metric_value, threshold_value,"
                    + " created_at, acknowledged_at from alert_events order by id desc limit ?",
                cap);
        } catch (Exception ex) {
            return List.of();
        }
    }

    public void acknowledge(long id) {
        try {
            jdbc.update(
                "update alert_events set acknowledged_at = ? where id = ? and acknowledged_at is null",
                System.currentTimeMillis(), id);
        } catch (Exception ignored) {
        }
    }

    private Map<String, Object> buildResult(
        MetricsSnapshot snapshot, List<Map<String, Object>> active, long now
    ) {
        int critical = 0;
        int warning = 0;
        int info = 0;
        for (Map<String, Object> alert : active) {
            String severity = String.valueOf(alert.get("severity"));
            if ("CRITICAL".equals(severity)) {
                critical++;
            } else if ("WARNING".equals(severity)) {
                warning++;
            } else {
                info++;
            }
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("evaluatedAt", lastEvaluatedAt == 0 ? now : lastEvaluatedAt);
        result.put("generatedAt", now);
        result.put("active", active);
        result.put("counts", Map.of(
            "critical", critical, "warning", warning, "info", info, "total", active.size()));
        result.put("thresholds", thresholds());
        return result;
    }

    private List<Map<String, Object>> evaluate(MetricsSnapshot snapshot) {
        List<Map<String, Object>> active = new ArrayList<>();
        Map<String, Object> window = metrics.window(WINDOW_MINUTES);

        double errorRateWarning = config.getDouble("alert.errorRate.warning", 2.0);
        double errorRateCritical = config.getDouble("alert.errorRate.critical", 5.0);
        double serverErrorRateCritical = config.getDouble("alert.serverErrorRate.critical", 1.0);
        double latencyWarningMs = config.getDouble("alert.latency.warningMs", 2000.0);
        double latencyCriticalMs = config.getDouble("alert.latency.criticalMs", 5000.0);
        double poolWarning = config.getDouble("alert.pool.warning", 75.0);
        double poolCritical = config.getDouble("alert.pool.critical", 90.0);
        double heapWarning = config.getDouble("alert.heap.warning", 85.0);
        double heapCritical = config.getDouble("alert.heap.critical", 95.0);
        double frontErrorsWarning = config.getDouble("alert.frontErrors.warning", 50.0);
        double frontErrorsCritical = config.getDouble("alert.frontErrors.critical", 200.0);
        double jobsWithoutBidsWarning = config.getDouble("alert.jobsWithoutBids.warning", 1.0);

        double errorRate = number(window, "errorRate");
        double serverErrorRate = number(window, "serverErrorRate");
        double p95 = number(window, "p95Ms");
        long requests = longFrom(window, "requests");
        boolean hasTraffic = requests >= 5;

        // 1. Errores de la API
        if (hasTraffic && (serverErrorRate > serverErrorRateCritical || errorRate > errorRateCritical)) {
            active.add(alert(
                "api_errors",
                "CRITICAL",
                "Tasa de error alta en la API",
                "Ventana de 5 min: " + errorRate + " % de errores ("
                    + serverErrorRate + " % son 5xx) con " + requests + " peticiones.",
                Math.max(errorRate, serverErrorRate),
                errorRateCritical,
                "%"));
        } else if (hasTraffic && errorRate > errorRateWarning) {
            active.add(alert(
                "api_errors",
                "WARNING",
                "Errores por encima de lo habitual",
                "Ventana de 5 min: " + errorRate + " % de errores en " + requests + " peticiones.",
                errorRate,
                errorRateWarning,
                "%"));
        }

        // 2. Latencia p95
        if (hasTraffic && p95 > latencyCriticalMs) {
            active.add(alert(
                "api_latency",
                "CRITICAL",
                "Latencia p95 muy alta",
                "La latencia p95 en los últimos 5 min es " + p95 + " ms.",
                p95,
                latencyCriticalMs,
                "ms"));
        } else if (hasTraffic && p95 > latencyWarningMs) {
            active.add(alert(
                "api_latency",
                "WARNING",
                "Latencia p95 elevada",
                "La latencia p95 en los últimos 5 min es " + p95 + " ms.",
                p95,
                latencyWarningMs,
                "ms"));
        }

        // 3. Pool de conexiones a la base de datos
        int poolActive = snapshot.dbPoolActive();
        int poolTotal = Math.max(1, snapshot.dbPoolTotal());
        int poolMax = Math.max(poolTotal, 1);
        Map<String, Object> db = database.snapshot();
        int poolConfiguredMax = intFrom(db, "pool.max", poolTotal);
        int limit = poolConfiguredMax > 0 ? poolConfiguredMax : poolTotal;
        double poolUsage = limit > 0 ? (poolActive * 100.0 / limit) : 0.0;
        if (poolUsage >= poolCritical) {
            active.add(alert(
                "db_pool",
                "CRITICAL",
                "Pool de base de datos casi agotado",
                "Conexiones activas: " + poolActive + " de " + limit + " (" + round(poolUsage) + " %).",
                poolUsage,
                poolCritical,
                "%"));
        } else if (poolUsage >= poolWarning) {
            active.add(alert(
                "db_pool",
                "WARNING",
                "Uso alto del pool de base de datos",
                "Conexiones activas: " + poolActive + " de " + limit + " (" + round(poolUsage) + " %).",
                poolUsage,
                poolWarning,
                "%"));
        }
        try {
            int waiting = intFrom(db, "pool.waiting", 0);
            if (waiting > 0) {
                active.add(alert(
                    "db_pool_wait",
                    "WARNING",
                    "Peticiones esperando conexión a la base de datos",
                    waiting + " hilo(s) esperando conexión a MySQL.",
                    waiting,
                    1.0,
                    "hilos"));
            }
        } catch (Exception ignored) {
        }

        // 4. Memoria heap
        double heapUsage = snapshot.heapMax() > 0
            ? (snapshot.heapUsed() * 100.0 / snapshot.heapMax())
            : 0.0;
        if (heapUsage >= heapCritical) {
            active.add(alert(
                "heap",
                "CRITICAL",
                "Memoria heap crítica",
                "Uso de heap: " + round(heapUsage) + " % (" + bytes(snapshot.heapUsed())
                    + " de " + bytes(snapshot.heapMax()) + ").",
                heapUsage,
                heapCritical,
                "%"));
        } else if (heapUsage >= heapWarning) {
            active.add(alert(
                "heap",
                "WARNING",
                "Uso alto de memoria heap",
                "Uso de heap: " + round(heapUsage) + " %.",
                heapUsage,
                heapWarning,
                "%"));
        }

        // 5. Errores del frontend
        long frontErrors = snapshot.frontErrors();
        if (frontErrors >= frontErrorsCritical) {
            active.add(alert(
                "front_errors",
                "CRITICAL",
                "Muchos errores del frontend",
                "El buffer en memoria acumula " + frontErrors + " errores de la app.",
                frontErrors,
                frontErrorsCritical,
                "errores"));
        } else if (frontErrors >= frontErrorsWarning) {
            active.add(alert(
                "front_errors",
                "WARNING",
                "Errores del frontend por encima de lo habitual",
                "El buffer en memoria acumula " + frontErrors + " errores de la app.",
                frontErrors,
                frontErrorsWarning,
                "errores"));
        }

        // 6. Suscripciones por vencer pronto
        long expiring = queryLong(
            "select count(*) from subscriptions where status = 'ACTIVE' and expires_at between ? and ?",
            System.currentTimeMillis(), System.currentTimeMillis() + 3L * 86_400_000L);
        if (expiring > 0) {
            active.add(alert(
                "subscriptions_expiring",
                "INFO",
                "Suscripciones Pro por vencer en los próximos 3 días",
                expiring + " suscripción(es) activa(s) vencen en 3 días o menos.",
                expiring,
                1.0,
                "suscripciones"));
        }

        // 7. Solicitudes abiertas sin ofertas
        long noBids = snapshot.jobsWithoutBids();
        if (noBids >= jobsWithoutBidsWarning) {
            active.add(alert(
                "jobs_without_bids",
                "WARNING",
                "Solicitudes abiertas sin ofertas",
                noBids + " solicitud(es) llevan más de 24 h abiertas sin ninguna oferta.",
                noBids,
                jobsWithoutBidsWarning,
                "solicitudes"));
        }

        persistTransitions(active);
        return active;
    }

    private MetricsSnapshot capture() {
        Map<String, Object> window = metrics.window(WINDOW_MINUTES);
        Map<String, Object> jvmSnapshot = jvm.snapshot();
        Map<String, Object> dbSnapshot = database.snapshot();
        Map<String, Object> front = frontLogs.summary();

        long heapUsed = longFrom(jvmSnapshot, "heapUsed");
        long heapMax = longFrom(jvmSnapshot, "heapMax");
        Map<String, Object> pool = (dbSnapshot.get("pool") instanceof Map<?, ?> m)
            ? (Map<String, Object>) m
            : Map.of();
        int poolActive = intFrom(pool, "active", 0);
        int poolTotal = intFrom(pool, "total", 0);

        long activeSubscriptions = queryLong("select count(*) from subscriptions where status = 'ACTIVE'");
        double mrr = queryDouble(
            "select coalesce(sum(case when plan = 'MONTHLY' then amount else amount / 12 end), 0)"
                + " from subscriptions where status = 'ACTIVE'");
        long openJobs = queryLong(
            "select count(*) from jobs where status not in ('COMPLETED', 'CANCELLED')");
        long jobsWithoutBids = queryLong(
            "select count(*) from jobs j where j.status not in ('COMPLETED', 'CANCELLED')"
                + " and j.created_at < ?"
                + " and not exists (select 1 from bids b where b.job_id = j.id)",
            System.currentTimeMillis() - 86_400_000L);

        long windowRequests = longFrom(window, "requests");
        long windowErrors = longFrom(window, "errors");
        double errorRate = number(window, "errorRate");
        double avgMs = number(window, "avgMs");
        double p95Ms = number(window, "p95Ms");
        double rps = windowRequests / (double) (WINDOW_MINUTES * 60);

        return new MetricsSnapshot(
            System.currentTimeMillis(),
            windowRequests,
            windowErrors,
            round(errorRate),
            round(avgMs),
            round(p95Ms),
            round(rps),
            heapUsed,
            heapMax,
            poolActive,
            poolTotal,
            metrics.externalTotal(),
            longFrom(front, "today"),
            longFrom(front, "errors"),
            activeSubscriptions,
            round(mrr),
            openJobs,
            jobsWithoutBids);
    }

    private void persistTransitions(List<Map<String, Object>> active) {
        ensureStateLoaded();
        Set<String> activeKeys = new HashSet<>();
        for (Map<String, Object> alert : active) {
            String key = String.valueOf(alert.get("key"));
            String severity = String.valueOf(alert.get("severity"));
            activeKeys.add(key);
            openAlerts.put(key, new LinkedHashMap<>(alert));
            String previous = openSeverity.get(key);
            if (!severity.equals(previous)) {
                insertEvent(key, severity, "OPEN", alert);
                openSeverity.put(key, severity);
            }
        }
        for (String key : new ArrayList<>(openSeverity.keySet())) {
            if (activeKeys.contains(key)) {
                continue;
            }
            Map<String, Object> previous = openAlerts.getOrDefault(key, Map.of());
            insertEvent(
                key,
                String.valueOf(previous.getOrDefault("severity", "INFO")),
                "RESOLVED",
                previous);
            openSeverity.remove(key);
            openAlerts.remove(key);
        }
    }

    private void insertEvent(String key, String severity, String status, Map<String, Object> alert) {
        try {
            String title = String.valueOf(alert.getOrDefault("title", key));
            String detail = String.valueOf(alert.getOrDefault("detail", ""));
            Object value = alert.get("value");
            Object threshold = alert.get("threshold");
            jdbc.update(
                "insert into alert_events (alert_key, severity, status, title, detail, metric_value,"
                    + " threshold_value, created_at) values (?, ?, ?, ?, ?, ?, ?, ?)",
                key,
                severity,
                status,
                title.length() > 160 ? title.substring(0, 159) + "…" : title,
                detail.length() > 500 ? detail.substring(0, 499) + "…" : detail,
                value instanceof Number n ? n.doubleValue() : null,
                threshold instanceof Number n ? n.doubleValue() : null,
                System.currentTimeMillis());
        } catch (Exception ignored) {
        }
    }

    private void ensureStateLoaded() {
        if (stateLoaded) {
            return;
        }
        synchronized (this) {
            if (stateLoaded) {
                return;
            }
            try {
                List<Map<String, Object>> rows = jdbc.queryForList(
                    "select e.alert_key, e.severity, e.title, e.detail from alert_events e"
                        + " join (select alert_key, max(id) id from alert_events group by alert_key) m"
                        + " on m.id = e.id where e.status = 'OPEN'");
                for (Map<String, Object> row : rows) {
                    String key = String.valueOf(row.get("alert_key"));
                    Map<String, Object> alert = new LinkedHashMap<>();
                    alert.put("key", key);
                    alert.put("severity", row.get("severity"));
                    alert.put("title", row.get("title"));
                    alert.put("detail", row.get("detail"));
                    openAlerts.put(key, alert);
                    openSeverity.put(key, String.valueOf(row.get("severity")));
                }
            } catch (Exception ignored) {
            }
            stateLoaded = true;
        }
    }

    private Map<String, Object> alert(
        String key, String severity, String title, String detail, double value, double threshold, String unit
    ) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("key", key);
        map.put("severity", severity);
        map.put("title", title);
        map.put("detail", detail);
        map.put("value", round(value));
        map.put("threshold", threshold);
        map.put("unit", unit);
        map.put("since", System.currentTimeMillis());
        return map;
    }

    private Map<String, Object> thresholds() {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("errorRateWarning", config.getDouble("alert.errorRate.warning", 2.0));
        out.put("errorRateCritical", config.getDouble("alert.errorRate.critical", 5.0));
        out.put("serverErrorRateCritical", config.getDouble("alert.serverErrorRate.critical", 1.0));
        out.put("latencyWarningMs", config.getDouble("alert.latency.warningMs", 2000.0));
        out.put("latencyCriticalMs", config.getDouble("alert.latency.criticalMs", 5000.0));
        out.put("poolWarning", config.getDouble("alert.pool.warning", 75.0));
        out.put("poolCritical", config.getDouble("alert.pool.critical", 90.0));
        out.put("heapWarning", config.getDouble("alert.heap.warning", 85.0));
        out.put("heapCritical", config.getDouble("alert.heap.critical", 95.0));
        out.put("frontErrorsWarning", config.getDouble("alert.frontErrors.warning", 50.0));
        out.put("frontErrorsCritical", config.getDouble("alert.frontErrors.critical", 200.0));
        out.put("jobsWithoutBidsWarning", config.getDouble("alert.jobsWithoutBids.warning", 1.0));
        return out;
    }

    private long queryLong(String sql, Object... args) {
        try {
            Long value = jdbc.queryForObject(sql, Long.class, args);
            return value == null ? 0L : value;
        } catch (Exception ex) {
            return 0L;
        }
    }

    private double queryDouble(String sql, Object... args) {
        try {
            Double value = jdbc.queryForObject(sql, Double.class, args);
            return value == null ? 0.0 : value;
        } catch (Exception ex) {
            return 0.0;
        }
    }

    private long longFrom(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value instanceof Number n) {
            return n.longValue();
        }
        try {
            return Long.parseLong(String.valueOf(value));
        } catch (Exception ex) {
            return 0L;
        }
    }

    private int intFrom(Map<String, Object> map, String key, int fallback) {
        Object value = map.get(key);
        if (value instanceof Number n) {
            return n.intValue();
        }
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (Exception ex) {
            return fallback;
        }
    }

    private double number(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value instanceof Number n) {
            return n.doubleValue();
        }
        try {
            return Double.parseDouble(String.valueOf(value));
        } catch (Exception ex) {
            return 0.0;
        }
    }

    private static double round(double value) {
        if (Double.isNaN(value) || Double.isInfinite(value)) {
            return 0.0;
        }
        return Math.round(value * 100.0) / 100.0;
    }

    private static String bytes(long value) {
        if (value < 1024) {
            return value + " B";
        }
        if (value < 1024 * 1024) {
            return Math.round(value / 1024.0) + " KB";
        }
        if (value < 1024L * 1024 * 1024) {
            return Math.round(value / (1024.0 * 1024)) + " MB";
        }
        return String.format(java.util.Locale.ROOT, "%.1f GB", value / (1024.0 * 1024 * 1024));
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> map(Object value) {
        if (value instanceof Map<?, ?> m) {
            return (Map<String, Object>) m;
        }
        return Map.of();
    }

    @SuppressWarnings("unused")
    private static <T> List<T> emptyList() {
        return Collections.emptyList();
    }
}

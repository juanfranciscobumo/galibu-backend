package com.galibu.backend.monitor;

import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * Histórico persistido de métricas del backend. Cada fila es una foto tomada
 * por el planificador de alertas; se conservan 30 días para no crecer sin
 * control en la base de datos de producción.
 */
@Service
public class MetricsHistoryStore {

    private final JdbcTemplate jdbc;

    public MetricsHistoryStore(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void record(MetricsSnapshot snapshot) {
        jdbc.update(
            "insert into metrics_history (captured_at, requests, errors, error_rate, avg_ms, p95_ms, rps,"
                + " heap_used, heap_max, db_pool_active, db_pool_total, external_requests, front_events,"
                + " front_errors, active_subscriptions, mrr, open_jobs, jobs_without_bids)"
                + " values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
            snapshot.capturedAt(),
            snapshot.requests(),
            snapshot.errors(),
            snapshot.errorRate(),
            snapshot.avgMs(),
            snapshot.p95Ms(),
            snapshot.rps(),
            snapshot.heapUsed(),
            snapshot.heapMax(),
            snapshot.dbPoolActive(),
            snapshot.dbPoolTotal(),
            snapshot.externalRequests(),
            snapshot.frontEvents(),
            snapshot.frontErrors(),
            snapshot.activeSubscriptions(),
            snapshot.mrr(),
            snapshot.openJobs(),
            snapshot.jobsWithoutBids());
    }

    /** Últimas fotos dentro de la ventana de horas indicada (más reciente primero). */
    public List<Map<String, Object>> recent(int hours) {
        long from = System.currentTimeMillis() - Math.max(1, hours) * 3_600_000L;
        return jdbc.queryForList(
            "select captured_at, requests, errors, error_rate, avg_ms, p95_ms, rps,"
                + " heap_used, heap_max, db_pool_active, db_pool_total, external_requests,"
                + " front_events, front_errors, active_subscriptions, mrr, open_jobs, jobs_without_bids"
                + " from metrics_history where captured_at >= ? order by captured_at desc limit 2000",
            from);
    }

    public void prune(int days) {
        long cutoff = System.currentTimeMillis() - Math.max(1, days) * 86_400_000L;
        jdbc.update("delete from metrics_history where captured_at < ?", cutoff);
    }
}

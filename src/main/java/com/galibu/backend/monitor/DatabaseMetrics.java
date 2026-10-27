package com.galibu.backend.monitor;

import com.zaxxer.hikari.HikariDataSource;
import com.zaxxer.hikari.HikariPoolMXBean;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.sql.DataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** Estado de MySQL/MariaDB: pool de conexiones, latencia, tamaño y contadores. */
@Component
public class DatabaseMetrics {

    private static final long CACHE_MS = 1500;
    private static final List<String> MAIN_TABLES =
        List.of("users", "jobs", "bids", "ratings", "wallet_transactions", "devices");

    private final DataSource dataSource;
    private final JdbcTemplate jdbc;

    private volatile Map<String, Object> cached;
    private volatile long cachedAt;
    private volatile long lastQueries = -1;
    private volatile long lastQueriesAt;

    public DatabaseMetrics(DataSource dataSource) {
        this.dataSource = dataSource;
        this.jdbc = new JdbcTemplate(dataSource);
    }

    public Map<String, Object> snapshot() {
        long now = System.currentTimeMillis();
        if (cached != null && now - cachedAt < CACHE_MS) {
            return cached;
        }
        synchronized (this) {
            if (cached == null || System.currentTimeMillis() - cachedAt >= CACHE_MS) {
                cached = build();
                cachedAt = System.currentTimeMillis();
            }
            return cached;
        }
    }

    private Map<String, Object> build() {
        Map<String, Object> out = new LinkedHashMap<>();
        readPool(out);

        long started = System.nanoTime();
        try {
            jdbc.queryForObject("select 1", Integer.class);
            double ms = (System.nanoTime() - started) / 1_000_000.0;
            out.put("connected", true);
            out.put("latencyMs", Math.round(ms * 100.0) / 100.0);
        } catch (Exception e) {
            out.put("connected", false);
            out.put("error", e.getMessage());
            return out;
        }

        try {
            out.put("name", jdbc.queryForObject("select database()", String.class));
            out.put("version", jdbc.queryForObject("select version()", String.class));
        } catch (Exception e) {
            out.put("infoError", e.getMessage());
        }

        try {
            Map<String, Object> rowCounts = new LinkedHashMap<>();
            for (String table : MAIN_TABLES) {
                rowCounts.put(table, jdbc.queryForObject("select count(*) from " + table, Long.class));
            }
            out.put("rowCounts", rowCounts);
        } catch (Exception e) {
            out.put("rowCountsError", e.getMessage());
        }

        try {
            out.put("sizeBytes", jdbc.queryForObject(
                "select coalesce(sum(data_length + index_length), 0) from information_schema.tables"
                    + " where table_schema = database()", Long.class));
            List<Map<String, Object>> raw = jdbc.queryForList(
                "select table_name as name, table_rows as approxRows,"
                    + " coalesce(data_length, 0) + coalesce(index_length, 0) as bytes"
                    + " from information_schema.tables where table_schema = database()"
                    + " order by bytes desc");
            List<Map<String, Object>> tables = new java.util.ArrayList<>();
            for (Map<String, Object> row : raw) {
                Map<String, Object> table = new LinkedHashMap<>();
                table.put("name", String.valueOf(valueOf(row, "name")));
                table.put("rows", toLong(valueOf(row, "approxRows")));
                table.put("bytes", toLong(valueOf(row, "bytes")));
                tables.add(table);
            }
            out.put("tables", tables);
        } catch (Exception e) {
            out.put("tablesError", e.getMessage());
        }

        readServerStatus(out);
        return out;
    }

    private void readPool(Map<String, Object> out) {
        try {
            HikariDataSource hikari = dataSource.unwrap(HikariDataSource.class);
            Map<String, Object> pool = new LinkedHashMap<>();
            HikariPoolMXBean mx = hikari.getHikariPoolMXBean();
            if (mx != null) {
                pool.put("active", mx.getActiveConnections());
                pool.put("idle", mx.getIdleConnections());
                pool.put("total", mx.getTotalConnections());
                pool.put("waiting", mx.getThreadsAwaitingConnection());
            }
            pool.put("max", hikari.getMaximumPoolSize());
            pool.put("min", hikari.getMinimumIdle());
            out.put("pool", pool);
        } catch (Exception e) {
            out.put("poolError", e.getMessage());
        }
    }

    private void readServerStatus(Map<String, Object> out) {
        try {
            List<Map<String, Object>> rows = jdbc.queryForList(
                "show global status where Variable_name in"
                    + " ('Queries','Threads_connected','Uptime','Slow_queries',"
                    + "'Bytes_received','Bytes_sent','Aborted_connects')");
            Map<String, Long> values = new LinkedHashMap<>();
            for (Map<String, Object> row : rows) {
                Object name = valueOf(row, "Variable_name");
                Object value = valueOf(row, "Value");
                if (name != null && value != null) {
                    try {
                        values.put(name.toString(), Long.parseLong(value.toString()));
                    } catch (NumberFormatException ignored) {
                        // Valor no numérico: se omite.
                    }
                }
            }
            out.put("queries", values.get("Queries"));
            out.put("threadsConnected", values.get("Threads_connected"));
            out.put("slowQueries", values.get("Slow_queries"));
            out.put("uptimeSec", values.get("Uptime"));
            out.put("bytesReceived", values.get("Bytes_received"));
            out.put("bytesSent", values.get("Bytes_sent"));
            out.put("abortedConnects", values.get("Aborted_connects"));

            long queries = values.getOrDefault("Queries", -1L);
            long now = System.currentTimeMillis();
            if (queries >= 0 && lastQueries >= 0 && now > lastQueriesAt) {
                double qps = (queries - lastQueries) * 1000.0 / (now - lastQueriesAt);
                out.put("qps", Math.round(qps * 100.0) / 100.0);
            }
            if (queries >= 0) {
                lastQueries = queries;
                lastQueriesAt = now;
            }
        } catch (Exception e) {
            out.put("statusError", e.getMessage());
        }
    }

    private static Object valueOf(Map<String, Object> row, String key) {
        Object direct = row.get(key);
        if (direct != null) {
            return direct;
        }
        for (Map.Entry<String, Object> entry : row.entrySet()) {
            if (entry.getKey().equalsIgnoreCase(key)) {
                return entry.getValue();
            }
        }
        return null;
    }

    private static long toLong(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value == null) {
            return 0;
        }
        try {
            return Long.parseLong(value.toString());
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}

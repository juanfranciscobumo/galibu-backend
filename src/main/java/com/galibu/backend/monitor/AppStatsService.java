package com.galibu.backend.monitor;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * Estadísticas de producto y de uso para el dashboard: usuarios, solicitudes,
 * ofertas, calificaciones, billetera, tráfico diario y eventos del front.
 */
@Service
public class AppStatsService {

    private final JdbcTemplate jdbc;
    private final UsageLedger usage;
    private final FrontLogStore frontLogs;
    private final RequestTraceStore traces;

    public AppStatsService(
        JdbcTemplate jdbc,
        UsageLedger usage,
        FrontLogStore frontLogs,
        RequestTraceStore traces
    ) {
        this.jdbc = jdbc;
        this.usage = usage;
        this.frontLogs = frontLogs;
        this.traces = traces;
    }

    public Map<String, Object> snapshot() {
        ZoneId zone = ZoneId.systemDefault();
        LocalDate today = LocalDate.now(zone);
        LocalDate yesterday = today.minusDays(1);
        long todayStart = startOfDay(today, zone);
        long tomorrowStart = startOfDay(today.plusDays(1), zone);
        long yesterdayStart = startOfDay(yesterday, zone);
        long weekStart = startOfDay(today.minusDays(6), zone);
        long monthStart = startOfDay(today.minusDays(29), zone);

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("generatedAt", System.currentTimeMillis());
        out.put("today", today.toString());
        out.put("yesterday", yesterday.toString());

        Map<String, Object> users = new LinkedHashMap<>();
        Map<String, Object> jobs = new LinkedHashMap<>();
        Map<String, Object> bids = new LinkedHashMap<>();
        Map<String, Object> ratings = new LinkedHashMap<>();
        Map<String, Object> wallet = new LinkedHashMap<>();

        try {
            Map<String, Long> roles = countBy("select role, count(*) as c from users group by role", "role", "c");
            long totalUsers = roles.values().stream().mapToLong(Long::longValue).sum();
            users.put("total", totalUsers);
            users.put("clients", roles.getOrDefault("CLIENT", 0L));
            users.put("professionals", roles.getOrDefault("PROFESSIONAL", 0L));
            users.put("both", roles.getOrDefault("BOTH", 0L));
            users.put("verified", value("select count(*) from users where is_verified = 1"));
            users.put("google", value("select count(*) from users where auth_provider = 'GOOGLE'"));
            users.put("manual", value("select count(*) from users where auth_provider = 'LOCAL'"));
            users.put("newToday", countRange("users", "created_at", todayStart, tomorrowStart));
            users.put("newYesterday", countRange("users", "created_at", yesterdayStart, todayStart));
            users.put("new7d", countRange("users", "created_at", weekStart, tomorrowStart));
            users.put("new30d", countRange("users", "created_at", monthStart, tomorrowStart));
            users.put("old", value("select count(*) from users where created_at < ?", monthStart));
            users.put("activeToday", activeUsers(todayStart, tomorrowStart));
            users.put("activeYesterday", activeUsers(yesterdayStart, todayStart));
        } catch (Exception ex) {
            users.put("error", ex.getMessage());
        }

        try {
            Map<String, Long> statuses = countBy("select status, count(*) as c from jobs group by status", "status", "c");
            jobs.put("total", statuses.values().stream().mapToLong(Long::longValue).sum());
            jobs.put("open", statuses.getOrDefault("OPEN", 0L));
            jobs.put("negotiating", statuses.getOrDefault("NEGOTIATING", 0L));
            jobs.put("accepted", statuses.getOrDefault("ACCEPTED", 0L));
            jobs.put("inProgress", statuses.getOrDefault("IN_PROGRESS", 0L));
            jobs.put("completed", statuses.getOrDefault("COMPLETED", 0L));
            jobs.put("cancelled", statuses.getOrDefault("CANCELLED", 0L));
            jobs.put("active", jobs.get("open") instanceof Long o
                ? o + (Long) jobs.get("negotiating") + (Long) jobs.get("accepted") + (Long) jobs.get("inProgress")
                : 0L);
            jobs.put("closed", (Long) jobs.get("completed") + (Long) jobs.get("cancelled"));
            jobs.put("createdToday", countRange("jobs", "created_at", todayStart, tomorrowStart));
            jobs.put("createdYesterday", countRange("jobs", "created_at", yesterdayStart, todayStart));
            jobs.put("created7d", countRange("jobs", "created_at", weekStart, tomorrowStart));
            jobs.put("byStatus", statuses);
            jobs.put("topCategories", rows(
                "select coalesce(nullif(category, ''), 'Sin categoría') as name, count(*) as count"
                    + " from jobs group by category order by count desc limit 8"));
        } catch (Exception ex) {
            jobs.put("error", ex.getMessage());
        }

        try {
            Map<String, Long> statuses = countBy("select status, count(*) as c from bids group by status", "status", "c");
            bids.put("total", statuses.values().stream().mapToLong(Long::longValue).sum());
            bids.put("pending", statuses.getOrDefault("PENDING", 0L));
            bids.put("accepted", statuses.getOrDefault("ACCEPTED", 0L));
            bids.put("rejected", statuses.getOrDefault("REJECTED", 0L));
            bids.put("today", countRange("bids", "created_at", todayStart, tomorrowStart));
            bids.put("yesterday", countRange("bids", "created_at", yesterdayStart, todayStart));
        } catch (Exception ex) {
            bids.put("error", ex.getMessage());
        }

        try {
            ratings.put("total", value("select count(*) from ratings"));
            ratings.put("avgStars", decimal("select coalesce(avg(stars), 0) from ratings"));
            ratings.put("today", countRange("ratings", "created_at", todayStart, tomorrowStart));
            ratings.put("yesterday", countRange("ratings", "created_at", yesterdayStart, todayStart));
        } catch (Exception ex) {
            ratings.put("error", ex.getMessage());
        }

        try {
            wallet.put("recharges", value("select count(*) from wallet_transactions where type = 'RECHARGE'"));
            wallet.put("rechargedCop", decimal("select coalesce(sum(amount), 0) from wallet_transactions where type = 'RECHARGE'"));
            wallet.put("rechargesToday", value(
                "select count(*) from wallet_transactions where type = 'RECHARGE' and created_at >= ? and created_at < ?",
                todayStart, tomorrowStart));
            wallet.put("rechargedTodayCop", decimal(
                "select coalesce(sum(amount), 0) from wallet_transactions where type = 'RECHARGE'"
                    + " and created_at >= ? and created_at < ?", todayStart, tomorrowStart));
        } catch (Exception ex) {
            wallet.put("error", ex.getMessage());
        }

        List<Map<String, Object>> days = usage.recentDays();
        Map<String, Object> usageOut = new LinkedHashMap<>();
        usageOut.put("today", findDay(days, today.toString()));
        usageOut.put("yesterday", findDay(days, yesterday.toString()));
        usageOut.put("days", days);
        usageOut.put("status", usage.status());

        Map<String, Object> devices = new LinkedHashMap<>();
        try {
            Map<String, Object> frontSummary = frontLogs.summary();
            devices.put("today", frontSummary.getOrDefault("devicesToday", 0L));
            devices.put("usersToday", frontSummary.getOrDefault("usersToday", 0L));
            devices.put("buffered", frontSummary.getOrDefault("buffered", 0L));
        } catch (Exception ex) {
            devices.put("error", ex.getMessage());
        }

        out.put("users", users);
        out.put("jobs", jobs);
        out.put("bids", bids);
        out.put("ratings", ratings);
        out.put("wallet", wallet);
        out.put("usage", usageOut);
        out.put("devices", devices);
        out.put("front", frontLogs.summary());
        out.put("traces", traces.summary());
        return out;
    }

    private long activeUsers(long from, long to) {
        Long value = jdbc.queryForObject(
            "select count(distinct uid) from ("
                + "select client_id as uid from jobs where updated_at >= ? and updated_at < ?"
                + " union all select professional_id as uid from jobs where professional_id is not null"
                + " and updated_at >= ? and updated_at < ?"
                + " union all select professional_id as uid from bids where created_at >= ? and created_at < ?"
                + " union all select rater_id as uid from ratings where created_at >= ? and created_at < ?"
                + " union all select id as uid from users where updated_at >= ? and updated_at < ?"
                + ") activity",
            Long.class,
            from, to, from, to, from, to, from, to, from, to);
        return value == null ? 0 : value;
    }

    private long countRange(String table, String column, long from, long to) {
        Long value = jdbc.queryForObject(
            "select count(*) from " + table + " where " + column + " >= ? and " + column + " < ?",
            Long.class, from, to);
        return value == null ? 0 : value;
    }

    private long value(String sql, Object... args) {
        Long value = jdbc.queryForObject(sql, Long.class, args);
        return value == null ? 0 : value;
    }

    private double decimal(String sql, Object... args) {
        Double value = jdbc.queryForObject(sql, Double.class, args);
        if (value == null || value.isNaN() || value.isInfinite()) {
            return 0;
        }
        return Math.round(value * 100.0) / 100.0;
    }

    private Map<String, Long> countBy(String sql, String nameKey, String countKey) {
        Map<String, Long> out = new LinkedHashMap<>();
        for (Map<String, Object> row : jdbc.queryForList(sql)) {
            Object name = row.get(nameKey);
            Object count = row.get(countKey);
            if (name == null) {
                continue;
            }
            long value = count instanceof Number number ? number.longValue() : 0L;
            out.put(String.valueOf(name).toUpperCase(Locale.ROOT), value);
        }
        return out;
    }

    private List<Map<String, Object>> rows(String sql) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (Map<String, Object> row : jdbc.queryForList(sql)) {
            Map<String, Object> item = new LinkedHashMap<>();
            for (Map.Entry<String, Object> entry : row.entrySet()) {
                String key = entry.getKey().toLowerCase(Locale.ROOT);
                Object value = entry.getValue();
                if (value instanceof Number number) {
                    item.put(key, number.longValue());
                } else {
                    item.put(key, value == null ? "—" : String.valueOf(value));
                }
            }
            out.add(item);
        }
        return out;
    }

    private static Map<String, Object> findDay(List<Map<String, Object>> days, String date) {
        for (Map<String, Object> day : days) {
            if (date.equals(day.get("date"))) {
                return day;
            }
        }
        Map<String, Object> empty = new LinkedHashMap<>();
        empty.put("date", date);
        empty.put("requests", 0L);
        empty.put("errors", 0L);
        empty.put("avgMs", 0.0);
        empty.put("maxMs", 0L);
        return empty;
    }

    private static long startOfDay(LocalDate date, ZoneId zone) {
        return date.atStartOfDay(zone).toInstant().toEpochMilli();
    }
}

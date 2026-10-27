package com.galibu.backend.monitor;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Component;

/**
 * Buffer en memoria de logs/eventos enviados por las apps Android.
 * Se usa para la pestaña "Logs front" y para las estadísticas de uso.
 */
@Component
public class FrontLogStore {

    private static final int MAX_ENTRIES = 5000;
    private static final int MAX_MESSAGE = 4000;
    private static final int MAX_METADATA_KEYS = 20;
    private static final int MAX_METADATA_VALUE = 500;

    private final ArrayDeque<Entry> entries = new ArrayDeque<>();
    private long totalReceived;
    private long dropped;
    private final long startedAt = System.currentTimeMillis();

    public synchronized void addAll(List<FrontLogEntry> batch) {
        if (batch == null || batch.isEmpty()) {
            return;
        }
        int accepted = 0;
        for (FrontLogEntry item : batch) {
            if (item == null) {
                continue;
            }
            if (accepted++ >= 100) {
                dropped++;
                continue;
            }
            Entry entry = normalize(item);
            entries.addLast(entry);
            totalReceived++;
        }
        while (entries.size() > MAX_ENTRIES) {
            entries.removeFirst();
            dropped++;
        }
    }

    public synchronized List<Map<String, Object>> recent(
        String level,
        String app,
        String query,
        int limit
    ) {
        String wantedLevel = level == null ? "ALL" : level.trim().toUpperCase(Locale.ROOT);
        String wantedApp = app == null ? "ALL" : app.trim().toLowerCase(Locale.ROOT);
        String needle = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        int max = Math.max(1, Math.min(limit, 500));
        List<Map<String, Object>> out = new ArrayList<>();
        List<Entry> copy = new ArrayList<>(entries);
        for (int i = copy.size() - 1; i >= 0 && out.size() < max; i--) {
            Entry entry = copy.get(i);
            if (!levelMatches(wantedLevel, entry.level)) {
                continue;
            }
            if (!wantedApp.isEmpty() && !"all".equals(wantedApp)
                && !wantedApp.equals(entry.app.toLowerCase(Locale.ROOT))) {
                continue;
            }
            if (!needle.isEmpty() && !entry.matches(needle)) {
                continue;
            }
            out.add(entry.toMap());
        }
        return out;
    }

    public synchronized Map<String, Object> summary() {
        LocalDate today = LocalDate.now();
        LocalDate yesterday = today.minusDays(1);
        long todayStart = startOfDay(today);
        long tomorrowStart = startOfDay(today.plusDays(1));
        long yesterdayStart = startOfDay(yesterday);

        long todayCount = 0;
        long yesterdayCount = 0;
        long errors = 0;
        long warns = 0;
        Set<String> usersToday = new HashSet<>();
        Set<String> devicesToday = new HashSet<>();
        Map<String, Long> byLevel = new HashMap<>();
        Map<String, Long> byApp = new HashMap<>();
        Map<String, Long> byEvent = new HashMap<>();
        Map<String, Long> byScreen = new HashMap<>();

        for (Entry entry : entries) {
            if (entry.at >= todayStart && entry.at < tomorrowStart) {
                todayCount++;
                if (!entry.userId.isBlank()) usersToday.add(entry.userId);
                if (!entry.device.isBlank()) devicesToday.add(entry.device);
            } else if (entry.at >= yesterdayStart && entry.at < todayStart) {
                yesterdayCount++;
            }
            if ("ERROR".equals(entry.level)) errors++;
            if ("WARN".equals(entry.level)) warns++;
            bump(byLevel, entry.level);
            bump(byApp, entry.app.isBlank() ? "desconocida" : entry.app);
            if (!entry.event.isBlank()) bump(byEvent, entry.event);
            if (!entry.screen.isBlank()) bump(byScreen, entry.screen);
        }

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("buffered", entries.size());
        out.put("received", totalReceived);
        out.put("dropped", dropped);
        out.put("startedAt", startedAt);
        out.put("today", todayCount);
        out.put("yesterday", yesterdayCount);
        out.put("errors", errors);
        out.put("warns", warns);
        out.put("usersToday", usersToday.size());
        out.put("devicesToday", devicesToday.size());
        out.put("byLevel", sortedCounts(byLevel, 10));
        out.put("byApp", sortedCounts(byApp, 10));
        out.put("byEvent", sortedCounts(byEvent, 20));
        out.put("byScreen", sortedCounts(byScreen, 20));
        return out;
    }

    public synchronized Map<String, Object> status() {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("buffered", entries.size());
        out.put("received", totalReceived);
        out.put("dropped", dropped);
        out.put("capacity", MAX_ENTRIES);
        out.put("startedAt", startedAt);
        return out;
    }

    private static Entry normalize(FrontLogEntry item) {
        long at = item.at() == null || item.at() <= 0 ? System.currentTimeMillis() : item.at();
        return new Entry(
            at,
            clean(item.level(), 16, "INFO").toUpperCase(Locale.ROOT),
            clean(item.app(), 30, "desconocida").toLowerCase(Locale.ROOT),
            clean(item.tag(), 80, ""),
            truncate(item.message(), MAX_MESSAGE),
            clean(item.requestId(), 128, ""),
            clean(item.userId(), 80, ""),
            clean(item.role(), 30, ""),
            clean(item.device(), 120, ""),
            clean(item.version(), 40, ""),
            clean(item.screen(), 120, ""),
            clean(item.event(), 120, ""),
            metadata(item.metadata())
        );
    }

    private static Map<String, String> metadata(Map<String, Object> raw) {
        if (raw == null || raw.isEmpty()) {
            return Map.of();
        }
        Map<String, String> out = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : raw.entrySet()) {
            if (out.size() >= MAX_METADATA_KEYS) {
                break;
            }
            if (entry.getKey() == null || entry.getValue() == null) {
                continue;
            }
            String key = clean(entry.getKey(), 60, "");
            if (key.isBlank()) {
                continue;
            }
            String value = String.valueOf(entry.getValue());
            out.put(key, truncate(value, MAX_METADATA_VALUE));
        }
        return out;
    }

    private static boolean levelMatches(String wanted, String level) {
        if (wanted.isEmpty() || "ALL".equals(wanted)) {
            return true;
        }
        if ("EVENT".equals(wanted)) {
            return "EVENT".equals(level);
        }
        int wantedSeverity = severity(wanted);
        return severity(level) >= wantedSeverity;
    }

    private static int severity(String level) {
        return switch (level) {
            case "ERROR" -> 3;
            case "WARN" -> 2;
            case "INFO", "EVENT" -> 1;
            default -> 0;
        };
    }

    private static long startOfDay(LocalDate date) {
        return date.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli();
    }

    private static void bump(Map<String, Long> map, String key) {
        map.merge(key == null || key.isBlank() ? "—" : key, 1L, Long::sum);
    }

    private static List<Map<String, Object>> sortedCounts(Map<String, Long> counts, int limit) {
        List<Map.Entry<String, Long>> ordered = new ArrayList<>(counts.entrySet());
        ordered.sort(Comparator
            .comparingLong((Map.Entry<String, Long> e) -> e.getValue()).reversed()
            .thenComparing(Map.Entry::getKey));
        List<Map<String, Object>> out = new ArrayList<>();
        for (int i = 0; i < ordered.size() && i < limit; i++) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("name", ordered.get(i).getKey());
            item.put("count", ordered.get(i).getValue());
            out.add(item);
        }
        return out;
    }

    private static String clean(String value, int max, String fallback) {
        if (value == null) {
            return fallback;
        }
        String clean = value.replaceAll("[\\r\\n\\t]", " ").trim();
        if (clean.isEmpty()) {
            return fallback;
        }
        return clean.length() <= max ? clean : clean.substring(0, max);
    }

    private static String truncate(String value, int max) {
        if (value == null) {
            return "";
        }
        return value.length() <= max ? value : value.substring(0, max - 1) + "…";
    }

    private record Entry(
        long at,
        String level,
        String app,
        String tag,
        String message,
        String requestId,
        String userId,
        String role,
        String device,
        String version,
        String screen,
        String event,
        Map<String, String> metadata
    ) {
        boolean matches(String needle) {
            return contains(requestId, needle) || contains(userId, needle) || contains(tag, needle)
                || contains(message, needle) || contains(screen, needle) || contains(event, needle)
                || contains(device, needle);
        }

        private static boolean contains(String value, String needle) {
            return value != null && value.toLowerCase(Locale.ROOT).contains(needle);
        }

        Map<String, Object> toMap() {
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("at", at);
            out.put("level", level);
            out.put("app", app);
            out.put("tag", tag);
            out.put("message", message);
            out.put("requestId", requestId);
            out.put("userId", userId);
            out.put("role", role);
            out.put("device", device);
            out.put("version", version);
            out.put("screen", screen);
            out.put("event", event);
            out.put("metadata", metadata);
            return out;
        }
    }
}

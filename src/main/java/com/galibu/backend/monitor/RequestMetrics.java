package com.galibu.backend.monitor;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/**
 * Métricas en memoria del tráfico HTTP de la API.
 *
 * <p>Guarda totales, una serie por segundo (últimos 2 min), una serie por
 * minuto (últimas 2 h), el detalle por endpoint, la distribución de códigos
 * de estado y las últimas peticiones. Las peticiones del propio dashboard
 * y de /actuator no se contabilizan.</p>
 */
@Component
public class RequestMetrics {

    private static final int SECONDS_WINDOW = 120;
    private static final int MINUTES_WINDOW = 120;
    private static final int RECENT_MAX = 120;
    private static final int ERRORS_MAX = 150;
    private static final int SAMPLES_MAX = 400;
    private static final int DETAIL_MAX = 700;

    private static final Pattern NUMERIC = Pattern.compile("\\d+");
    private static final Pattern UUID = Pattern.compile(
        "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}");
    private static final Pattern PREFIXED_ID = Pattern.compile(
        "(job|bid|wtx|ses|usr|msg|dev)_[A-Za-z0-9_\\-]{6,}");

    private volatile long startedAt = System.currentTimeMillis();

    private final AtomicLong totalRequests = new AtomicLong();
    private final AtomicLong totalErrors = new AtomicLong();
    private final AtomicLong totalMs = new AtomicLong();
    private final AtomicLong maxMs = new AtomicLong();
    private final Map<String, Endpoint> endpoints = new ConcurrentHashMap<>();
    private final Map<Integer, AtomicLong> statusCodes = new ConcurrentHashMap<>();
    private final ArrayDeque<RecentRequest> recent = new ArrayDeque<>();
    private final ArrayDeque<ErrorEntry> errors = new ArrayDeque<>();
    private final SlidingSamples globalSamples = new SlidingSamples(SAMPLES_MAX);

    private final Bucket[] seconds = createBuckets(SECONDS_WINDOW);
    private final Bucket[] minutes = createBuckets(MINUTES_WINDOW);
    private final Object bucketLock = new Object();

    private static Bucket[] createBuckets(int size) {
        Bucket[] buckets = new Bucket[size];
        for (int i = 0; i < size; i++) {
            buckets[i] = new Bucket();
        }
        return buckets;
    }

    public void record(String method, String path, int status, long durationMs, String client) {
        record(method, path, status, durationMs, client, null);
    }

    public void record(
        String method,
        String path,
        int status,
        long durationMs,
        String client,
        String detail
    ) {
        if (path == null || path.isBlank()) {
            return;
        }
        if (path.startsWith("/api/monitor")
            || path.startsWith("/monitor")
            || path.startsWith("/actuator")
            || path.equals("/favicon.ico")) {
            return;
        }

        String normalized = normalize(path);
        long now = System.currentTimeMillis();
        long ms = Math.max(0, durationMs);
        boolean error = status >= 400;

        totalRequests.incrementAndGet();
        totalMs.addAndGet(ms);
        if (error) {
            totalErrors.incrementAndGet();
        }
        bumpMax(maxMs, ms);
        globalSamples.add(ms);
        statusCodes.computeIfAbsent(status, code -> new AtomicLong()).incrementAndGet();

        Endpoint endpoint = endpoints.computeIfAbsent(
            method + " " + normalized, key -> new Endpoint(method, normalized));
        endpoint.record(ms, status, error, now);

        long second = now / 1000;
        long minute = now / 60_000;
        synchronized (bucketLock) {
            Bucket sec = seconds[(int) (second % SECONDS_WINDOW)];
            if (sec.index != second) {
                sec.reset(second);
            }
            sec.add(ms, error);
            Bucket min = minutes[(int) (minute % MINUTES_WINDOW)];
            if (min.index != minute) {
                min.reset(minute);
            }
            min.add(ms, error);
        }

        synchronized (recent) {
            recent.addFirst(new RecentRequest(now, method, normalized, status, ms, client));
            while (recent.size() > RECENT_MAX) {
                recent.removeLast();
            }
        }

        if (error) {
            synchronized (errors) {
                errors.addFirst(new ErrorEntry(
                    now, method, normalized, status, ms, client, truncate(detail)));
                while (errors.size() > ERRORS_MAX) {
                    errors.removeLast();
                }
            }
        }
    }

    public Map<String, Object> snapshot() {
        long now = System.currentTimeMillis();
        long requests = totalRequests.get();
        long errorCount = totalErrors.get();
        long total = totalMs.get();

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("startedAt", startedAt);
        out.put("serverTime", now);

        Map<String, Object> totals = new LinkedHashMap<>();
        totals.put("requests", requests);
        totals.put("errors", errorCount);
        totals.put("errorRate", requests == 0 ? 0.0 : round(errorCount * 100.0 / requests));
        totals.put("avgMs", requests == 0 ? 0.0 : round(total * 1.0 / requests));
        totals.put("maxMs", maxMs.get());
        totals.put("p95Ms", globalSamples.percentile(95));
        totals.put("rps", recentPerSecond(now));
        out.put("totals", totals);

        Map<String, Object> series = new LinkedHashMap<>();
        series.put("perSecond", series(seconds, SECONDS_WINDOW, 1000L, now));
        series.put("perMinute", series(minutes, MINUTES_WINDOW, 60_000L, now));
        out.put("series", series);

        List<Map<String, Object>> endpointList = new ArrayList<>();
        for (Endpoint endpoint : endpoints.values()) {
            endpointList.add(endpoint.snapshot());
        }
        endpointList.sort(Comparator
            .comparingLong((Map<String, Object> m) -> (long) m.get("count"))
            .reversed()
            .thenComparing(m -> (String) m.get("path")));
        out.put("endpoints", endpointList);

        List<Map<String, Object>> codes = new ArrayList<>();
        statusCodes.forEach((code, count) ->
            codes.add(Map.of("code", code, "count", count.get())));
        codes.sort(Comparator.comparingLong((Map<String, Object> m) -> (long) m.get("count")).reversed());
        out.put("statusCodes", codes);

        List<Map<String, Object>> recents = new ArrayList<>();
        synchronized (recent) {
            for (RecentRequest request : recent) {
                recents.add(request.toMap());
            }
        }
        out.put("recent", recents);

        List<Map<String, Object>> errorList = new ArrayList<>();
        synchronized (this.errors) {
            for (ErrorEntry entry : this.errors) {
                errorList.add(entry.toMap());
            }
        }
        out.put("errors", errorList);
        return out;
    }

    public void reset() {
        totalRequests.set(0);
        totalErrors.set(0);
        totalMs.set(0);
        maxMs.set(0);
        endpoints.clear();
        statusCodes.clear();
        globalSamples.clear();
        synchronized (recent) {
            recent.clear();
        }
        synchronized (errors) {
            errors.clear();
        }
        synchronized (bucketLock) {
            for (Bucket bucket : seconds) {
                bucket.reset(-1);
            }
            for (Bucket bucket : minutes) {
                bucket.reset(-1);
            }
        }
        startedAt = System.currentTimeMillis();
    }

    /**
     * Reemplaza los identificadores dinámicos por {@code {id}} para agrupar
     * las rutas (ej. /api/jobs/job_abc123/bids -> /api/jobs/{id}/bids).
     */
    static String normalize(String path) {
        String clean = path;
        int query = clean.indexOf('?');
        if (query >= 0) {
            clean = clean.substring(0, query);
        }
        String[] parts = clean.split("/");
        StringBuilder out = new StringBuilder();
        for (String part : parts) {
            if (part.isEmpty()) {
                continue;
            }
            out.append('/').append(isId(part) ? "{id}" : part);
        }
        return out.length() == 0 ? "/" : out.toString();
    }

    private static boolean isId(String segment) {
        return NUMERIC.matcher(segment).matches()
            || UUID.matcher(segment).matches()
            || PREFIXED_ID.matcher(segment).matches();
    }

    private double recentPerSecond(long now) {
        long second = now / 1000;
        long count = 0;
        synchronized (bucketLock) {
            for (int i = 0; i < 5; i++) {
                Bucket bucket = seconds[(int) ((second - i) % SECONDS_WINDOW)];
                if (bucket.index == second - i) {
                    count += bucket.count;
                }
            }
        }
        return round(count / 5.0);
    }

    private List<Map<String, Object>> series(Bucket[] buckets, int size, long unitMs, long now) {
        long current = now / unitMs;
        List<Map<String, Object>> out = new ArrayList<>(size);
        synchronized (bucketLock) {
            for (int i = size - 1; i >= 0; i--) {
                long index = current - i;
                Bucket bucket = buckets[(int) (index % size)];
                boolean hit = bucket.index == index;
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("t", index * unitMs);
                item.put("count", hit ? bucket.count : 0);
                item.put("errors", hit ? bucket.errors : 0);
                item.put("avgMs", hit && bucket.count > 0 ? round(bucket.sumMs * 1.0 / bucket.count) : 0.0);
                item.put("maxMs", hit ? bucket.maxMs : 0);
                out.add(item);
            }
        }
        return out;
    }

    private static void bumpMax(AtomicLong max, long value) {
        long previous;
        do {
            previous = max.get();
            if (value <= previous) {
                return;
            }
        } while (!max.compareAndSet(previous, value));
    }

    private static double round(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    private record RecentRequest(long at, String method, String path, int status, long ms, String client) {
        Map<String, Object> toMap() {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("at", at);
            map.put("method", method);
            map.put("path", path);
            map.put("status", status);
            map.put("ms", ms);
            map.put("client", client);
            return map;
        }
    }

    private record ErrorEntry(
        long at,
        String method,
        String path,
        int status,
        long ms,
        String client,
        String detail
    ) {
        Map<String, Object> toMap() {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("at", at);
            map.put("method", method);
            map.put("path", path);
            map.put("status", status);
            map.put("ms", ms);
            map.put("client", client);
            map.put("detail", detail);
            return map;
        }
    }

    private static String truncate(String text) {
        if (text == null) {
            return null;
        }
        String clean = text.strip().replaceAll("\\s+", " ");
        if (clean.isEmpty()) {
            return null;
        }
        return clean.length() <= DETAIL_MAX ? clean : clean.substring(0, DETAIL_MAX - 1) + "…";
    }

    private static final class Endpoint {
        private final String method;
        private final String path;
        private final AtomicLong count = new AtomicLong();
        private final AtomicLong errors = new AtomicLong();
        private final AtomicLong sumMs = new AtomicLong();
        private final AtomicLong maxMs = new AtomicLong();
        private final SlidingSamples samples = new SlidingSamples(SAMPLES_MAX);
        private final ArrayDeque<Long> recentTimes = new ArrayDeque<>();
        private volatile int lastStatus;
        private volatile long lastMs;
        private volatile long lastAt;

        private Endpoint(String method, String path) {
            this.method = method;
            this.path = path;
        }

        private void record(long ms, int status, boolean error, long now) {
            count.incrementAndGet();
            sumMs.addAndGet(ms);
            if (error) {
                errors.incrementAndGet();
            }
            bumpMax(maxMs, ms);
            samples.add(ms);
            synchronized (recentTimes) {
                recentTimes.addLast(now);
                while (!recentTimes.isEmpty() && now - recentTimes.peekFirst() > 5000L) {
                    recentTimes.pollFirst();
                }
            }
            lastStatus = status;
            lastMs = ms;
            lastAt = now;
        }

        private Map<String, Object> snapshot() {
            long total = count.get();
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("method", method);
            map.put("path", path);
            map.put("count", total);
            map.put("errors", errors.get());
            map.put("rps", recentRps(System.currentTimeMillis()));
            map.put("avgMs", total == 0 ? 0.0 : round(sumMs.get() * 1.0 / total));
            map.put("p95Ms", samples.percentile(95));
            map.put("maxMs", maxMs.get());
            map.put("lastStatus", lastStatus);
            map.put("lastMs", lastMs);
            map.put("lastAt", lastAt);
            return map;
        }

        private double recentRps(long now) {
            synchronized (recentTimes) {
                while (!recentTimes.isEmpty() && now - recentTimes.peekFirst() > 5000L) {
                    recentTimes.pollFirst();
                }
                return Math.round((recentTimes.size() / 5.0) * 100.0) / 100.0;
            }
        }
    }

    private static final class Bucket {
        private long index = -1;
        private long count;
        private long errors;
        private long sumMs;
        private long maxMs;

        private void reset(long index) {
            this.index = index;
            this.count = 0;
            this.errors = 0;
            this.sumMs = 0;
            this.maxMs = 0;
        }

        private void add(long ms, boolean error) {
            count++;
            sumMs += ms;
            if (error) {
                errors++;
            }
            if (ms > maxMs) {
                maxMs = ms;
            }
        }
    }

    private static final class SlidingSamples {
        private final int[] values;
        private int size;
        private int next;

        private SlidingSamples(int capacity) {
            this.values = new int[capacity];
        }

        private synchronized void add(long value) {
            values[next] = (int) Math.min(Integer.MAX_VALUE, Math.max(0, value));
            next = (next + 1) % values.length;
            if (size < values.length) {
                size++;
            }
        }

        private synchronized double percentile(int percentile) {
            if (size == 0) {
                return 0;
            }
            int[] copy = Arrays.copyOf(values, size);
            Arrays.sort(copy);
            int index = (int) Math.ceil(percentile / 100.0 * size) - 1;
            return copy[Math.max(0, Math.min(index, size - 1))];
        }

        private synchronized void clear() {
            size = 0;
            next = 0;
            Arrays.fill(values, 0);
        }
    }
}

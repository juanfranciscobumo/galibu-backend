package com.galibu.backend.monitor;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Acumula el tráfico del día y lo persiste en bloques. Así el dashboard puede
 * mostrar uso de hoy/ayer incluso después de reiniciar el backend sin escribir
 * en MySQL en cada petición.
 */
@Component
public class UsageLedger {

    private final ApiDailyUsageRepository repository;
    private final Map<LocalDate, Delta> pending = new ConcurrentHashMap<>();
    private volatile long lastFlushAt;
    private volatile String lastError;

    public UsageLedger(ApiDailyUsageRepository repository) {
        this.repository = repository;
    }

    public void record(int status, long durationMs) {
        LocalDate today = LocalDate.now();
        Delta delta = pending.computeIfAbsent(today, key -> new Delta());
        delta.requests.incrementAndGet();
        if (status >= 400) {
            delta.errors.incrementAndGet();
        }
        long ms = Math.max(0, durationMs);
        delta.totalMs.addAndGet(ms);
        bumpMax(delta.maxMs, ms);
    }

    @Scheduled(
        fixedDelayString = "${app.monitor.usage-flush-ms:10000}",
        initialDelayString = "${app.monitor.usage-flush-initial-ms:10000}"
    )
    public synchronized void flush() {
        for (Map.Entry<LocalDate, Delta> entry : pending.entrySet()) {
            LocalDate date = entry.getKey();
            Delta delta = entry.getValue();
            long requests = delta.requests.getAndSet(0);
            long errors = delta.errors.getAndSet(0);
            long totalMs = delta.totalMs.getAndSet(0);
            long maxMs = delta.maxMs.getAndSet(0);
            if (requests == 0 && errors == 0 && totalMs == 0 && maxMs == 0) {
                continue;
            }
            try {
                ApiDailyUsage row = repository.findById(date).orElseGet(() -> new ApiDailyUsage(date));
                row.setRequests(row.getRequests() + requests);
                row.setErrors(row.getErrors() + errors);
                row.setTotalMs(row.getTotalMs() + totalMs);
                row.setMaxMs(Math.max(row.getMaxMs(), maxMs));
                repository.save(row);
                lastFlushAt = System.currentTimeMillis();
                lastError = null;
            } catch (Exception ex) {
                // Si la base falla, devolvemos el bloque a memoria para el próximo intento.
                delta.requests.addAndGet(requests);
                delta.errors.addAndGet(errors);
                delta.totalMs.addAndGet(totalMs);
                bumpMax(delta.maxMs, maxMs);
                lastError = ex.getMessage();
            }
        }
    }

    /** Últimos días (más reciente primero), mezclando lo pendiente de persistir. */
    public List<Map<String, Object>> recentDays() {
        List<ApiDailyUsage> rows;
        try {
            rows = repository.findTop14ByOrderByUsageDateDesc();
        } catch (Exception ex) {
            lastError = ex.getMessage();
            rows = List.of();
        }
        Map<LocalDate, ApiDailyUsage> byDate = new LinkedHashMap<>();
        for (ApiDailyUsage row : rows) {
            byDate.put(row.getUsageDate(), row);
        }
        for (Map.Entry<LocalDate, Delta> entry : pending.entrySet()) {
            Delta delta = entry.getValue();
            long req = delta.requests.get();
            long err = delta.errors.get();
            long sum = delta.totalMs.get();
            long max = delta.maxMs.get();
            if (req == 0 && err == 0 && sum == 0 && max == 0) {
                continue;
            }
            ApiDailyUsage row = byDate.computeIfAbsent(entry.getKey(), ApiDailyUsage::new);
            row.setRequests(row.getRequests() + req);
            row.setErrors(row.getErrors() + err);
            row.setTotalMs(row.getTotalMs() + sum);
            row.setMaxMs(Math.max(row.getMaxMs(), max));
        }
        List<ApiDailyUsage> merged = new ArrayList<>(byDate.values());
        merged.sort(Comparator.comparing(ApiDailyUsage::getUsageDate).reversed());
        List<Map<String, Object>> out = new ArrayList<>();
        for (ApiDailyUsage row : merged) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("date", row.getUsageDate().toString());
            item.put("requests", row.getRequests());
            item.put("errors", row.getErrors());
            item.put("avgMs", row.getRequests() == 0 ? 0.0
                : Math.round(row.getTotalMs() * 100.0 / row.getRequests()) / 100.0);
            item.put("maxMs", row.getMaxMs());
            out.add(item);
        }
        return out;
    }

    public Map<String, Object> status() {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("lastFlushAt", lastFlushAt);
        out.put("pendingDays", pending.size());
        out.put("lastError", lastError);
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

    private static final class Delta {
        private final AtomicLong requests = new AtomicLong();
        private final AtomicLong errors = new AtomicLong();
        private final AtomicLong totalMs = new AtomicLong();
        private final AtomicLong maxMs = new AtomicLong();
    }
}

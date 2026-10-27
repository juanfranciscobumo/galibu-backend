package com.galibu.backend.monitor;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDate;

/**
 * Contadores diarios de tráfico de la API. Se persisten para que las
 * estadísticas de "hoy" y "ayer" sobrevivan a un reinicio del backend.
 */
@Entity
@Table(name = "api_daily_usage")
public class ApiDailyUsage {

    @Id
    @Column(name = "usage_date", nullable = false)
    private LocalDate usageDate;

    @Column(nullable = false)
    private long requests;

    @Column(nullable = false)
    private long errors;

    @Column(name = "total_ms", nullable = false)
    private long totalMs;

    @Column(name = "max_ms", nullable = false)
    private long maxMs;

    public ApiDailyUsage() {
    }

    public ApiDailyUsage(LocalDate usageDate) {
        this.usageDate = usageDate;
    }

    public LocalDate getUsageDate() {
        return usageDate;
    }

    public void setUsageDate(LocalDate usageDate) {
        this.usageDate = usageDate;
    }

    public long getRequests() {
        return requests;
    }

    public void setRequests(long requests) {
        this.requests = requests;
    }

    public long getErrors() {
        return errors;
    }

    public void setErrors(long errors) {
        this.errors = errors;
    }

    public long getTotalMs() {
        return totalMs;
    }

    public void setTotalMs(long totalMs) {
        this.totalMs = totalMs;
    }

    public long getMaxMs() {
        return maxMs;
    }

    public void setMaxMs(long maxMs) {
        this.maxMs = maxMs;
    }
}

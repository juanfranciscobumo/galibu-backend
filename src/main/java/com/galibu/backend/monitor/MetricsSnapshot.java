package com.galibu.backend.monitor;

/**
 * Foto de las métricas clave en un instante. Se persiste cada pocos minutos
 * para poder ver la evolución del backend y detectar tendencias.
 */
public record MetricsSnapshot(
    long capturedAt,
    long requests,
    long errors,
    double errorRate,
    double avgMs,
    double p95Ms,
    double rps,
    long heapUsed,
    long heapMax,
    int dbPoolActive,
    int dbPoolTotal,
    long externalRequests,
    long frontEvents,
    long frontErrors,
    long activeSubscriptions,
    double mrr,
    long openJobs,
    long jobsWithoutBids
) {
}

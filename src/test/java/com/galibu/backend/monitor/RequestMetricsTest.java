package com.galibu.backend.monitor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class RequestMetricsTest {

    @Test
    void normalizesDynamicSegments() {
        assertEquals("/api/jobs/{id}/bids",
            RequestMetrics.normalize("/api/jobs/job_82f8a94208594ac0bde79ff845292553/bids"));
        assertEquals("/api/users/{id}",
            RequestMetrics.normalize("/api/users/3552d9d7-1219-4bb5-829a-727a3bdb6422"));
        assertEquals("/api/geo/municipalities/{id}",
            RequestMetrics.normalize("/api/geo/municipalities/12"));
        assertEquals("/api/jobs", RequestMetrics.normalize("/api/jobs"));
        assertEquals("/api/jobs", RequestMetrics.normalize("/api/jobs?status=OPEN"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void aggregatesTrafficByEndpointAndStatus() {
        RequestMetrics metrics = new RequestMetrics();
        metrics.record("GET", "/api/jobs", 200, 10, "10.0.0.1");
        metrics.record("GET", "/api/jobs", 200, 30, "10.0.0.1");
        metrics.record("POST", "/api/jobs/job_abcdef123456/accept", 500, 90, "10.0.0.2");
        metrics.record("GET", "/api/monitor/data", 200, 1, "10.0.0.1");

        Map<String, Object> snapshot = metrics.snapshot();
        Map<String, Object> totals = (Map<String, Object>) snapshot.get("totals");
        assertEquals(3L, totals.get("requests"));
        assertEquals(1L, totals.get("errors"));
        assertEquals(43.33, (double) totals.get("avgMs"), 0.01);
        assertEquals(90L, totals.get("maxMs"));
        // Con solo 3 muestras el p95 (nearest-rank) cae en la máxima.
        assertEquals(90.0, (double) totals.get("p95Ms"), 0.001);

        List<Map<String, Object>> endpoints =
            (List<Map<String, Object>>) snapshot.get("endpoints");
        assertEquals(2, endpoints.size());
        Map<String, Object> top = endpoints.get(0);
        assertEquals("GET", top.get("method"));
        assertEquals("/api/jobs", top.get("path"));
        assertEquals(2L, top.get("count"));
        assertEquals(20.0, (double) top.get("avgMs"), 0.01);

        Map<String, Object> series = (Map<String, Object>) snapshot.get("series");
        List<Map<String, Object>> perSecond =
            (List<Map<String, Object>>) series.get("perSecond");
        assertEquals(120, perSecond.size());
        Map<String, Object> last = perSecond.get(perSecond.size() - 1);
        assertEquals(3L, last.get("count"));
        assertEquals(1L, last.get("errors"));

        List<Map<String, Object>> codes =
            (List<Map<String, Object>>) snapshot.get("statusCodes");
        assertTrue(codes.stream()
            .anyMatch(code -> ((Number) code.get("code")).intValue() == 500));

        assertEquals(3, ((List<?>) snapshot.get("recent")).size());
    }

    @Test
    @SuppressWarnings("unchecked")
    void keepsErrorDetailsAndIgnoresFavicon() {
        RequestMetrics metrics = new RequestMetrics();
        metrics.record("POST", "/api/auth/login", 400, 8, "10.0.0.5",
            "Validación fallida: email: no debe estar vacío");
        metrics.record("GET", "/favicon.ico", 404, 1, "10.0.0.5");

        Map<String, Object> snapshot = metrics.snapshot();
        Map<String, Object> totals = (Map<String, Object>) snapshot.get("totals");
        assertEquals(1L, totals.get("requests"));
        assertEquals(1L, totals.get("errors"));

        List<Map<String, Object>> errors =
            (List<Map<String, Object>>) snapshot.get("errors");
        assertEquals(1, errors.size());
        Map<String, Object> error = errors.get(0);
        assertEquals(400, ((Number) error.get("status")).intValue());
        assertEquals("/api/auth/login", error.get("path"));
        assertEquals("Validación fallida: email: no debe estar vacío", error.get("detail"));

        metrics.reset();
        assertEquals(0, ((List<?>) metrics.snapshot().get("errors")).size());
    }

    @Test
    @SuppressWarnings("unchecked")
    void resetClearsCounters() {
        RequestMetrics metrics = new RequestMetrics();
        metrics.record("GET", "/api/jobs", 200, 10, "10.0.0.1");

        metrics.reset();

        Map<String, Object> totals = (Map<String, Object>) metrics.snapshot().get("totals");
        assertEquals(0L, totals.get("requests"));
        assertEquals(0L, totals.get("errors"));
        assertEquals(0.0, (double) totals.get("avgMs"), 0.001);
    }
}

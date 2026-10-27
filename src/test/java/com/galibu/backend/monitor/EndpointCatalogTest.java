package com.galibu.backend.monitor;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class EndpointCatalogTest {

    @Test
    void normalizesPathVariablesLikeRequestMetrics() {
        assertEquals("/api/jobs/{id}",
            EndpointCatalog.normalizePattern("/api/jobs/{jobId}"));
        assertEquals("/api/jobs/{id}/bids",
            EndpointCatalog.normalizePattern("/api/jobs/{jobId}/bids"));
        assertEquals("/api/geo/departments/{id}/municipalities",
            EndpointCatalog.normalizePattern("/api/geo/departments/{departmentId}/municipalities"));
        assertEquals("/api/jobs/{id}",
            EndpointCatalog.normalizePattern("/api/jobs/{id:[0-9]+}"));
        assertEquals("/api/jobs", EndpointCatalog.normalizePattern("/api/jobs"));
        assertEquals("/api/monitor/data", EndpointCatalog.normalizePattern("/api/monitor/data"));
    }
}

package com.galibu.backend.monitor;

import java.util.Map;

/** Entrada que envían las apps Android al endpoint público /api/front-logs. */
public record FrontLogEntry(
    Long at,
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
    Map<String, Object> metadata
) {
}

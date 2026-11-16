package com.galibu.backend.monitor;

import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Endpoint público para que las apps envíen sus logs/eventos. No requiere JWT
 * porque también debe registrar fallos de login o de red antes de autenticar.
 */
@RestController
@RequestMapping("/api/front-logs")
public class FrontLogController {

    private final FrontLogStore store;

    public FrontLogController(FrontLogStore store) {
        this.store = store;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void receive(@RequestBody List<FrontLogEntry> entries) {
        store.addAll(entries);
    }

    /**
     * Lista de logs con filtros extendidos: nivel, app, texto libre, usuario,
     * dispositivo, marca, modelo, versión de app, pantalla, evento y rango de
     * fechas (from/to en epoch ms).
     */
    @GetMapping
    public Map<String, Object> recent(
        @RequestParam(defaultValue = "ALL") String level,
        @RequestParam(defaultValue = "ALL") String app,
        @RequestParam(value = "q", required = false) String query,
        @RequestParam(value = "userId", required = false) String userId,
        @RequestParam(value = "device", required = false) String device,
        @RequestParam(value = "brand", required = false) String brand,
        @RequestParam(value = "model", required = false) String model,
        @RequestParam(value = "version", required = false) String version,
        @RequestParam(value = "screen", required = false) String screen,
        @RequestParam(value = "event", required = false) String event,
        @RequestParam(value = "from", required = false) Long from,
        @RequestParam(value = "to", required = false) Long to,
        @RequestParam(defaultValue = "200") int limit
    ) {
        List<Map<String, Object>> items = store.recentExtended(
            level, app, query, userId, device, brand, model, version,
            screen, event, from, to, limit
        );
        Map<String, Object> out = new java.util.LinkedHashMap<>();
        out.put("items", items);
        out.put("summary", store.summary());
        out.put("devices", store.deviceSummary());
        return out;
    }
}

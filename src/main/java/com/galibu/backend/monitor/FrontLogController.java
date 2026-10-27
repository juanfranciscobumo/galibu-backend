package com.galibu.backend.monitor;

import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
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
}

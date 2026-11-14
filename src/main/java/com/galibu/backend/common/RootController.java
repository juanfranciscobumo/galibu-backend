package com.galibu.backend.common;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Raíz del dominio. Los escáneres de internet suelen pedir {@code /} para
 * descubrir qué hay detrás; respondemos 204 sin contenido para no revelar la
 * API ni generar 401 que ensucien el monitor.
 */
@RestController
public class RootController {

    @GetMapping({"/", ""})
    public ResponseEntity<Void> root() {
        return ResponseEntity.noContent().build();
    }
}

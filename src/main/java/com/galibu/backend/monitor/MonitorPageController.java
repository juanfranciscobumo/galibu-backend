package com.galibu.backend.monitor;

import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/** Sirve las páginas HTML del dashboard (login y panel). */
@Controller
public class MonitorPageController {

    @GetMapping({"/monitor", "/monitor/"})
    public ResponseEntity<Resource> dashboard() {
        return page("monitor/index.html");
    }

    @GetMapping("/monitor/login")
    public ResponseEntity<Resource> login() {
        return page("monitor/login.html");
    }

    private static ResponseEntity<Resource> page(String classpath) {
        return ResponseEntity.ok()
            .contentType(MediaType.TEXT_HTML)
            .cacheControl(CacheControl.noStore())
            .body(new ClassPathResource(classpath));
    }
}

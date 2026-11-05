package com.galibu.backend.devices;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/devices")
public class DeviceController {

    private static final Logger log = LoggerFactory.getLogger(DeviceController.class);

    private final DeviceService service;

    public DeviceController(DeviceService service) {
        this.service = service;
    }

    @PostMapping("/token")
    public ResponseEntity<Void> registerToken(
        Authentication auth,
        @RequestBody DeviceTokenRequest request
    ) {
        log.info("devices registerToken userId={} platform={}", auth.getName(), request.platform());
        service.registerToken(auth.getName(), request.fcmToken(), request.platform());
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/token")
    public ResponseEntity<Void> removeToken(
        Authentication auth,
        @RequestBody DeviceTokenRequest request
    ) {
        log.info("devices removeToken userId={}", auth.getName());
        service.removeToken(request.fcmToken());
        return ResponseEntity.noContent().build();
    }

    public record DeviceTokenRequest(String fcmToken, String platform) {}
}
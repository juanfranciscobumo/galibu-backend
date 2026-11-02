package com.galibu.backend.devices;

import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/devices")
public class DeviceController {

    private final DeviceService service;

    public DeviceController(DeviceService service) {
        this.service = service;
    }

    @PostMapping("/token")
    public ResponseEntity<Void> registerToken(
        Authentication auth,
        @RequestBody DeviceTokenRequest request
    ) {
        service.registerToken(auth.getName(), request.fcmToken(), request.platform());
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/token")
    public ResponseEntity<Void> removeToken(
        Authentication auth,
        @RequestBody DeviceTokenRequest request
    ) {
        service.removeToken(request.fcmToken());
        return ResponseEntity.noContent().build();
    }

    public record DeviceTokenRequest(String fcmToken, String platform) {}
}

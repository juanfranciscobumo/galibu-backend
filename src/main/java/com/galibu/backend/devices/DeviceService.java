package com.galibu.backend.devices;

import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DeviceService {

    private final DeviceTokenRepository tokens;

    public DeviceService(DeviceTokenRepository tokens) {
        this.tokens = tokens;
    }

    @Transactional
    public void registerToken(String userId, String fcmToken, String platform) {
        tokens.upsertToken(userId, fcmToken, platform, System.currentTimeMillis());
    }

    @Transactional
    public void removeToken(String fcmToken) {
        tokens.deleteByFcmToken(fcmToken);
    }

    @Transactional(readOnly = true)
    public List<String> findTokensByUserId(String userId) {
        return tokens.findByUserId(userId).stream()
            .map(DeviceToken::getFcmToken)
            .toList();
    }
}

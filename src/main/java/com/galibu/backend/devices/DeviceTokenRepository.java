package com.galibu.backend.devices;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DeviceTokenRepository extends JpaRepository<DeviceToken, Long> {

    Optional<DeviceToken> findByFcmToken(String fcmToken);

    List<DeviceToken> findByUserId(String userId);

    void deleteByFcmToken(String fcmToken);

    /**
     * Registro idempotente por token. Un mismo dispositivo puede iniciar sesión
     * con cuentas distintas: el token debe reasignarse al último usuario en vez
     * de provocar un Duplicate entry (uq_devices_token).
     */
    @Modifying
    @Query(value = "INSERT INTO devices (user_id, fcm_token, platform, updated_at) "
        + "VALUES (:userId, :fcmToken, :platform, :updatedAt) AS new "
        + "ON DUPLICATE KEY UPDATE user_id = new.user_id, platform = new.platform, "
        + "updated_at = new.updated_at", nativeQuery = true)
    void upsertToken(
        @Param("userId") String userId,
        @Param("fcmToken") String fcmToken,
        @Param("platform") String platform,
        @Param("updatedAt") long updatedAt
    );
}

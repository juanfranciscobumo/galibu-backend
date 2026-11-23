package com.galibu.backend.notifications;

import com.galibu.backend.devices.DeviceService;
import com.google.firebase.FirebaseApp;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingException;
import com.google.firebase.messaging.Message;
import com.google.firebase.messaging.MessagingErrorCode;
import com.google.firebase.messaging.Notification;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Envío de notificaciones push (FCM) a los dispositivos registrados de un usuario.
 *
 * Requiere Firebase Admin SDK inicializado (FirebaseInitializer). Si no hay
 * credenciales de servicio configuradas, las llamadas se omiten y la app sigue
 * funcionando con polling de respaldo.
 *
 * Los tokens inválidos (app desinstalada/token rotado) se eliminan al vuelo para
 * no reintentar contra dispositivos muertos en cada evento.
 */
@Service
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);

    private final DeviceService devices;

    public NotificationService(DeviceService devices) {
        this.devices = devices;
    }

    /** Resultado de un envío (para diagnóstico desde el monitor). */
    public record PushReport(int tokens, int sent, List<String> errors) {}

    public boolean notifyUser(String userId, String title, String body, String jobId) {
        return send(userId, title, body, jobId).sent() > 0;
    }

    public void notifyUsers(List<String> userIds, String title, String body, String jobId) {
        if (userIds == null) return;
        for (String uid : userIds) {
            notifyUser(uid, title, body, jobId);
        }
    }

    /** Envía el push a todos los dispositivos del usuario y reporta el resultado. */
    public PushReport send(String userId, String title, String body, String jobId) {
        List<String> tokens = devices.findTokensByUserId(userId);
        if (tokens.isEmpty()) {
            log.info("[PUSH] sin dispositivos registrados user={} job={}", shortId(userId), jobId);
            return new PushReport(0, 0, List.of("sin dispositivos registrados"));
        }
        if (!isFirebaseReady()) {
            log.warn("[PUSH] FCM deshabilitado user={} job={} title={}", shortId(userId), jobId, title);
            return new PushReport(tokens.size(), 0, List.of("FCM deshabilitado"));
        }
        int sent = 0;
        List<String> errors = new ArrayList<>();
        for (String token : tokens) {
            try {
                Message.Builder builder = Message.builder()
                    .setToken(token)
                    .setNotification(Notification.builder()
                        .setTitle(title)
                        .setBody(body)
                        .build());
                if (jobId != null && !jobId.isBlank()) {
                    builder.putData("jobId", jobId);
                }
                FirebaseMessaging.getInstance().send(builder.build());
                sent++;
                log.info("[PUSH] ok user={} job={} token={}", shortId(userId), jobId, maskToken(token));
            } catch (FirebaseMessagingException e) {
                boolean dead = e.getMessagingErrorCode() == MessagingErrorCode.UNREGISTERED
                    || e.getMessagingErrorCode() == MessagingErrorCode.INVALID_ARGUMENT;
                log.warn("[PUSH] fallo user={} job={} token={} code={} msg={}{}",
                    shortId(userId), jobId, maskToken(token), e.getMessagingErrorCode(),
                    e.getMessage(), dead ? " (token eliminado)" : "");
                errors.add(maskToken(token) + ": " + e.getMessagingErrorCode());
                if (dead) {
                    devices.removeToken(token);
                }
            } catch (Exception e) {
                log.error("[PUSH] error inesperado user={} token={}", shortId(userId), maskToken(token), e);
                errors.add(maskToken(token) + ": " + e.getMessage());
            }
        }
        return new PushReport(tokens.size(), sent, errors);
    }

    private boolean isFirebaseReady() {
        return !FirebaseApp.getApps().isEmpty();
    }

    /** Nunca se registra el token completo en los logs. */
    private static String maskToken(String token) {
        if (token == null || token.length() <= 12) return "***";
        return token.substring(0, 8) + "..." + token.substring(token.length() - 4);
    }

    private static String shortId(String id) {
        return id == null || id.length() <= 8 ? String.valueOf(id) : id.substring(0, 8);
    }
}

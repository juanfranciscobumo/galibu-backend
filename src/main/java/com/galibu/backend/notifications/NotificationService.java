package com.galibu.backend.notifications;

import com.galibu.backend.devices.DeviceService;
import com.google.firebase.FirebaseApp;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingException;
import com.google.firebase.messaging.Message;
import com.google.firebase.messaging.MessagingErrorCode;
import com.google.firebase.messaging.Notification;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * Envío de notificaciones push (FCM) a los dispositivos registrados de un usuario.
 *
 * Requiere Firebase Admin SDK inicializado (FirebaseInitializer). Si no hay
 * credenciales de servicio configuradas, las llamadas se omiten y la app sigue
 * funcionando con polling de respaldo.
 */
@Service
public class NotificationService {

    private final DeviceService devices;

    public NotificationService(DeviceService devices) {
        this.devices = devices;
    }

    public boolean notifyUser(String userId, String title, String body, String jobId) {
        List<String> tokens = devices.findTokensByUserId(userId);
        if (tokens.isEmpty()) {
            return false;
        }
        if (!isFirebaseReady()) {
            System.out.println("[PUSH] (FCM deshabilitado) user=" + userId
                + " job=" + jobId + " title=" + title);
            return false;
        }
        boolean sent = false;
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
                sent = true;
            } catch (FirebaseMessagingException e) {
                System.err.println("[PUSH] fallo enviando a token " + token + ": " + e.getMessage());
                if (e.getMessagingErrorCode() == MessagingErrorCode.UNREGISTERED
                    || e.getMessagingErrorCode() == MessagingErrorCode.INVALID_ARGUMENT) {
                    devices.removeToken(token);
                }
            } catch (Exception e) {
                System.err.println("[PUSH] error inesperado: " + e.getMessage());
            }
        }
        return sent;
    }

    public void notifyUsers(List<String> userIds, String title, String body, String jobId) {
        if (userIds == null) return;
        for (String uid : userIds) {
            notifyUser(uid, title, body, jobId);
        }
    }

    private boolean isFirebaseReady() {
        return !FirebaseApp.getApps().isEmpty();
    }
}

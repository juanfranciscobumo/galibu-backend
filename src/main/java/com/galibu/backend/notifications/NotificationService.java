package com.galibu.backend.notifications;

import java.util.List;
import org.springframework.stereotype.Service;

/**
 * Servicio de notificaciones push (FCM).
 *
 * NOTA: El envío real mediante Firebase Admin SDK requiere un archivo de
 * credenciales de servicio (service-account.json) configurado en Render.
 * Mientras no esté disponible, este servicio solo registra la intención de
 * enviar y lo escribe en log. La app sigue funcionando con polling largo de
 * respaldo.
 */
@Service
public class NotificationService {

    /**
     * Envía una notificación push a todos los dispositivos de un usuario.
     * Devuelve true si se intentó enviar (independientemente de si FCM está configurado).
     */
    public boolean notifyUser(String userId, String title, String body, String jobId) {
        // TODO: Conectar Firebase Admin SDK cuando se dispongan las credenciales FCM en Render.
        // Ejemplo:
        // FirebaseMessaging.getInstance().sendAsync(Message.builder()...);
        System.out.println("[PUSH] user=" + userId + " job=" + jobId + " title=" + title + " body=" + body);
        return true;
    }

    public void notifyUsers(List<String> userIds, String title, String body, String jobId) {
        for (String uid : userIds) {
            notifyUser(uid, title, body, jobId);
        }
    }
}

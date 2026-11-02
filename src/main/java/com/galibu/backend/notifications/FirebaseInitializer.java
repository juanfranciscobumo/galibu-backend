package com.galibu.backend.notifications;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import jakarta.annotation.PostConstruct;
import java.io.ByteArrayInputStream;
import java.io.FileInputStream;
import java.nio.charset.StandardCharsets;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Inicializa Firebase Admin SDK si se dispone de credenciales de servicio.
 *
 * Configuración (Render / .env):
 *  - FIREBASE_SERVICE_ACCOUNT_JSON: contenido completo del service-account.json
 *    (recomendado en Render, como secret).
 *  - FIREBASE_SERVICE_ACCOUNT_PATH: ruta a un archivo service-account.json
 *    (alternativa para desarrollo local).
 *
 * Si ninguna está presente, Firebase queda deshabilitado y las notificaciones
 * push se omiten (la app sigue funcionando con polling de respaldo).
 */
@Component
public class FirebaseInitializer {

    @Value("${FIREBASE_SERVICE_ACCOUNT_JSON:}")
    private String serviceAccountJson;

    @Value("${FIREBASE_SERVICE_ACCOUNT_PATH:}")
    private String serviceAccountPath;

    @PostConstruct
    public void init() {
        if (!FirebaseApp.getApps().isEmpty()) {
            return;
        }
        try {
            com.google.auth.oauth2.GoogleCredentials credentials = null;
            if (serviceAccountJson != null && !serviceAccountJson.isBlank()) {
                credentials = GoogleCredentials.fromStream(
                    new ByteArrayInputStream(serviceAccountJson.getBytes(StandardCharsets.UTF_8)));
            } else if (serviceAccountPath != null && !serviceAccountPath.isBlank()) {
                credentials = GoogleCredentials.fromStream(new FileInputStream(serviceAccountPath));
            }

            if (credentials == null) {
                System.out.println("[FCM] Sin credenciales de servicio: push deshabilitado (polling de respaldo).");
                return;
            }

            FirebaseOptions options = FirebaseOptions.builder()
                .setCredentials(credentials)
                .build();
            FirebaseApp.initializeApp(options);
            System.out.println("[FCM] Firebase Admin SDK inicializado correctamente.");
        } catch (Exception e) {
            System.err.println("[FCM] Error inicializando Firebase Admin SDK: " + e.getMessage());
        }
    }
}

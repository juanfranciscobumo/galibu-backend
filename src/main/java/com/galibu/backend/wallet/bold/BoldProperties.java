package com.galibu.backend.wallet.bold;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Configuración de la pasarela Bold (pagos en línea).
 *
 * Las llaves se reciben por variables de entorno para no dejarlas escritas en
 * el repositorio:
 *   BOLD_API_KEY    -> llave de identidad (x-api-key)
 *   BOLD_SECRET_KEY -> llave secreta (firma del webhook)
 */
@Component
@ConfigurationProperties(prefix = "app.bold")
@Getter
@Setter
public class BoldProperties {

    private boolean enabled = true;

    private String apiKey = "";

    private String secretKey = "";

    private String apiUrl = "https://integrations.api.bold.co";

    /** URL https a la que Bold redirige al terminar el pago. */
    private String callbackUrl = "https://galibu.app/bold/return";

    /**
     * Solo para el ambiente de pruebas de Bold: allí la firma del webhook se
     * calcula con la llave secreta vacía. Nunca activar en producción.
     */
    private boolean testMode = false;

    private double minAmount = 1000.0;

    private double maxAmount = 5000000.0;

    public boolean isConfigured() {
        // La llave secreta solo se exige al validar webhooks; el checkout puede
        // funcionar con la llave de identidad.
        return enabled && apiKey != null && !apiKey.isBlank();
    }
}

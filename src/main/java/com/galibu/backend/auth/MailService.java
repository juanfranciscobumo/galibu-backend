package com.galibu.backend.auth;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import java.util.Properties;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestClient;

/**
 * Envío de correos: SMTP (local) + Resend HTTPS (Render).
 *
 * Render bloquea SMTP saliente (puerto 587) -> MailConnectException /
 * SocketTimeoutException. Por eso, si existe RESEND_API_KEY se envía por
 * HTTPS (443) vía Resend. Si no, se usa SMTP (úntil se configure Resend).
 *
 * Fail-closed: si ni SMTP ni Resend están configurados, /forgot-password
 * responde 503 en lugar de fingir que envió el código.
 */
@Service
public class MailService {

    private static final Logger log = LoggerFactory.getLogger(MailService.class);

    private final String host;
    private final int port;
    private final String username;
    private final String password;
    private final String from;
    private final String resendApiKey;
    private final String resendFrom;

    public MailService(
        @Value("${SMTP_HOST:}") String host,
        @Value("${SMTP_PORT:587}") int port,
        @Value("${SMTP_USERNAME:}") String username,
        @Value("${SMTP_PASSWORD:}") String password,
        @Value("${SMTP_FROM:}") String from,
        @Value("${RESEND_API_KEY:}") String resendApiKey,
        @Value("${RESEND_FROM:}") String resendFrom
    ) {
        this.host = host;
        this.port = port;
        this.username = username;
        this.password = password;
        this.from = from.isBlank() ? username : from;
        this.resendApiKey = resendApiKey == null ? "" : resendApiKey.trim();
        this.resendFrom = (resendFrom != null && !resendFrom.isBlank()) ? resendFrom.trim() : "Galibu <no-reply@galibu.app>";
    }

    public boolean isConfigured() {
        return isResendConfigured() || (!host.isBlank() && !username.isBlank() && !password.isBlank());
    }

    private boolean isResendConfigured() {
        return !resendApiKey.isBlank();
    }

    public void sendPasswordResetCode(String to, String code) {
        if (!isConfigured()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                "El envío de correos no está configurado en este servidor.");
        }
        String subject = "Galibu · Código para restablecer tu contraseña";
        String body = "Hola,\n\n" +
            "Tu código para restablecer tu contraseña en Galibu es: " + code + "\n\n" +
            "Este código vence en 15 minutos. Si no solicitaste este cambio, puedes ignorar este correo.\n\n" +
            "El equipo de Galibu";

        // Render: SMTP bloqueado -> usar HTTPS Resend si está configurado.
        if (isResendConfigured()) {
            if (sendViaResend(to.trim().toLowerCase(), subject, body)) {
                return;
            }
            log.warn("Resend falló, intentando SMTP como respaldo");
        }
        sendViaSmtp(to, subject, body);
    }

    /**
     * @return true si Resend respondió 2xx.
     */
    private boolean sendViaResend(String to, String subject, String body) {
        try {
            RestClient client = RestClient.create("https://api.resend.com");
            ResponseEntity<Map> resp = client.post()
                .uri("/emails")
                .header("Authorization", "Bearer " + resendApiKey)
                .header("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                .body(Map.of(
                    "from", resendFrom,
                    "to", to,
                    "subject", subject,
                    "text", body
                ))
                .retrieve()
                .toEntity(Map.class);
            if (resp.getStatusCode().is2xxSuccessful()) {
                log.info("Resend mail sent to={} from={}", to, resendFrom);
                return true;
            }
            log.error("Resend mail failed status={} body={} to={}", resp.getStatusCode(), resp.getBody(), to);
            return false;
        } catch (Exception ex) {
            log.error("Resend mail exception to={} error={}", to, ex.getMessage(), ex);
            return false;
        }
    }

    private void sendViaSmtp(String to, String subject, String body) {
        if (host.isBlank() || username.isBlank() || password.isBlank()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                "El envío de correos no está configurado en este servidor.");
        }
        JavaMailSenderImpl sender = new JavaMailSenderImpl();
        sender.setHost(host);
        sender.setPort(port);
        sender.setUsername(username);
        sender.setPassword(password);
        sender.setDefaultEncoding("UTF-8");

        Properties props = sender.getJavaMailProperties();
        props.put("mail.smtp.auth", "true");
        props.put("mail.smtp.starttls.enable", "true");
        props.put("mail.smtp.starttls.required", "true");
        props.put("mail.smtp.timeout", 10000);
        props.put("mail.smtp.connectiontimeout", 10000);
        props.put("mail.smtp.writetimeout", 10000);

        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(from);
        message.setTo(to.trim().toLowerCase());
        message.setSubject(subject);
        message.setText(body);
        try {
            sender.send(message);
            log.info("SMTP mail sent to={} host={} port={} from={}", to, host, port, from);
        } catch (RuntimeException ex) {
            // Conexión a smtp.gmail.com:587 bloqueada en Render -> timeout 10000.
            log.error("SMTP mail failed to={} host={} port={} from={} username={} error={}",
                to, host, port, from, username, ex.getMessage(), ex);
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                "No se pudo enviar el correo de recuperación. Inténtalo de nuevo más tarde.", ex);
        }
    }
}

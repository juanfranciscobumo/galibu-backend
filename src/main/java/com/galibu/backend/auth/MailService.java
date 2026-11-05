package com.galibu.backend.auth;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.Properties;

/**
 * Envío de correos vía SMTP (Gmail u otro proveedor).
 *
 * Fail-closed: si las variables SMTP_* no están definidas, /forgot-password
 * responde 503 en lugar de fingir que envió el código.
 */
@Service
public class MailService {

    private final String host;
    private final int port;
    private final String username;
    private final String password;
    private final String from;

    public MailService(
        @Value("${SMTP_HOST:}") String host,
        @Value("${SMTP_PORT:587}") int port,
        @Value("${SMTP_USERNAME:}") String username,
        @Value("${SMTP_PASSWORD:}") String password,
        @Value("${SMTP_FROM:}") String from
    ) {
        this.host = host;
        this.port = port;
        this.username = username;
        this.password = password;
        this.from = from.isBlank() ? username : from;
    }

    public boolean isConfigured() {
        return !host.isBlank() && !username.isBlank() && !password.isBlank();
    }

    public void sendPasswordResetCode(String to, String code) {
        if (!isConfigured()) {
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
        message.setSubject("Galibu · Código para restablecer tu contraseña");
        message.setText(
            "Hola,\n\n" +
            "Tu código para restablecer tu contraseña en Galibu es: " + code + "\n\n" +
            "Este código vence en 15 minutos. Si no solicitaste este cambio, puedes ignorar este correo.\n\n" +
            "El equipo de Galibu"
        );
        try {
            sender.send(message);
        } catch (RuntimeException ex) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                "No se pudo enviar el correo de recuperación. Inténtalo de nuevo más tarde.", ex);
        }
    }
}

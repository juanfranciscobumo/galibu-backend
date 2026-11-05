package com.galibu.backend.auth;

import com.galibu.backend.users.User;
import com.galibu.backend.users.UserRepository;
import java.security.SecureRandom;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * Recuperación de contraseña de un solo uso vía correo.
 *
 * - El código de 6 dígitos solo existe mientras dura el envío.
 * - Se almacena hasheado (BCrypt) igual que una contraseña.
 * - Expira a los 15 minutos.
 * - Si el correo no existe o es de Google, devuelve un error claro al usuario
 *   para que no quede esperando un correo que nunca llegará.
 */
@Service
public class PasswordResetService {

    private static final Logger log = LoggerFactory.getLogger(PasswordResetService.class);
    private static final long TOKEN_EXPIRY_MS = 15 * 60 * 1000L;
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final int MAX_RETRIES = 3;

    private final UserRepository users;
    private final PasswordResetTokenRepository tokens;
    private final PasswordEncoder passwordEncoder;
    private final MailService mail;

    public PasswordResetService(
        UserRepository users,
        PasswordResetTokenRepository tokens,
        PasswordEncoder passwordEncoder,
        MailService mail
    ) {
        this.users = users;
        this.tokens = tokens;
        this.passwordEncoder = passwordEncoder;
        this.mail = mail;
    }

    /**
     * Solicita un código de recuperación.
     *
     * - Si el correo no existe: error 400 "El correo no está registrado en Galibu".
     * - Si es cuenta de Google: error 400 indicando que se gestiona desde Google.
     * - Si SMTP no está configurado: error 503.
     * - Si todo está bien: envía el código de 6 dígitos y responde 204.
     */
    public void requestReset(String rawEmail) {
        String email = rawEmail.trim().toLowerCase();
        Optional<User> maybe = users.findByEmail(email);
        if (maybe.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "El correo no está registrado en Galibu");
        }

        User user = maybe.get();
        if (!"LOCAL".equals(user.getAuthProvider())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "Tu cuenta está vinculada a Google. Cambia tu contraseña desde tu cuenta de Google.");
        }

        // Fail-closed: si el correo no se puede enviar, abortamos antes de generar nada.
        if (!mail.isConfigured()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                "El envío de correos no está configurado en este servidor.");
        }

        int attempts = 0;
        while (true) {
            try {
                doRequestReset(email);
                return;
            } catch (CannotAcquireLockException ex) {
                attempts++;
                if (attempts >= MAX_RETRIES) {
                    log.error("Deadlock persistente al activar reset para email={} tras {} intentos",
                        email, attempts);
                    throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                        "No se pudo procesar la solicitud. Inténtalo de nuevo más tarde.");
                }
                log.warn("Deadlock al activar reset para email={}, reintento {}/{}",
                    email, attempts, MAX_RETRIES);
                try {
                    Thread.sleep(50 * attempts); // backoff exponencial ligero
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                        "No se pudo procesar la solicitud. Inténtalo de nuevo más tarde.");
                }
            }
        }
    }

    private void doRequestReset(String email) {
        User user = users.findByEmail(email)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "El correo no está registrado en Galibu"));

        long now = System.currentTimeMillis();
        tokens.invalidateActiveForUser(user.getId());

        String code = String.format("%06d", RANDOM.nextInt(1_000_000));
        PasswordResetToken token = new PasswordResetToken();
        token.setId(java.util.UUID.randomUUID().toString());
        token.setUserId(user.getId());
        token.setTokenHash(passwordEncoder.encode(code));
        token.setExpiresAt(now + TOKEN_EXPIRY_MS);
        token.setUsed(false);
        token.setCreatedAt(now);
        tokens.save(token);
        try {
            mail.sendPasswordResetCode(email, code);
        } catch (RuntimeException ex) {
            tokens.invalidateActiveForUser(user.getId());
            throw ex;
        }
    }

    /**
     * Restablece la contraseña previa validación del código de 6 dígitos
     * y de la expiración del token.
     */
    @Transactional
    public void resetPassword(String rawEmail, String code, String newPassword) {
        String email = rawEmail.trim().toLowerCase();
        Optional<User> maybe = users.findByEmail(email);
        if (maybe.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Código inválido o expirado");
        }
        long now = System.currentTimeMillis();
        PasswordResetToken token = tokens
            .findTopByUserIdAndUsedFalseAndExpiresAtGreaterThanOrderByCreatedAtDesc(maybe.get().getId(), now)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Código inválido o expirado"));

        if (!passwordEncoder.matches(code, token.getTokenHash())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Código inválido o expirado");
        }

        User user = maybe.get();
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        users.save(user);
        tokens.invalidateActiveForUser(user.getId());
    }
}
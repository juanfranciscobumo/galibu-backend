package com.galibu.backend.auth;

import com.galibu.backend.users.User;
import com.galibu.backend.users.UserRepository;
import java.security.SecureRandom;
import java.util.List;
import java.util.Optional;
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
 * - Si la cuenta no existe o es de Google, la respuesta es idéntica a un envío
 *   exitoso: no se revela qué correos están registrados.
 */
@Service
public class PasswordResetService {

    private static final long TOKEN_EXPIRY_MS = 15 * 60 * 1000L;
    private static final SecureRandom RANDOM = new SecureRandom();

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
     * Solicita un código de recuperación. Siempre responde 204 (sin exponer
     * si el email existe); solo envía correo si la cuenta existe y es LOCAL.
     */
    public void requestReset(String rawEmail) {
        String email = rawEmail.trim().toLowerCase();
        Optional<User> maybe = users.findByEmail(email);
        if (maybe.isEmpty()) return;

        User user = maybe.get();
        if (!"LOCAL".equals(user.getAuthProvider())) {
            // Cuenta de Google: no tiene contraseña en Galibu; la gestión es desde Google.
            return;
        }

        // Fail-closed: si el correo no se puede enviar, abortamos antes de generar nada.
        if (!mail.isConfigured()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                "El envío de correos no está configurado en este servidor.");
        }

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

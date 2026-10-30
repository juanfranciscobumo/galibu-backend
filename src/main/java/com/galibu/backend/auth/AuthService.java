package com.galibu.backend.auth;

import com.galibu.backend.common.TextUtils;
import com.galibu.backend.auth.dto.AuthResponse;
import com.galibu.backend.auth.dto.GoogleLoginRequest;
import com.galibu.backend.auth.dto.LoginRequest;
import com.galibu.backend.auth.dto.RegisterRequest;
import com.galibu.backend.auth.dto.UserDto;
import com.galibu.backend.security.JwtService;
import com.galibu.backend.users.User;
import com.galibu.backend.users.UserRepository;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import io.jsonwebtoken.Claims;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import com.galibu.backend.wallet.WalletService;

@Service
public class AuthService {

    private static final List<String> VALID_ROLES = List.of("CLIENT", "PROFESSIONAL", "BOTH");

    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final List<String> googleClientIds;

    public AuthService(
        UserRepository users,
        PasswordEncoder passwordEncoder,
        JwtService jwtService,
        @Value("${app.auth.google-client-ids:}") String googleClientIds
    ) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.googleClientIds = Arrays.stream(googleClientIds.split(","))
            .map(String::trim)
            .filter(s -> !s.isEmpty())
            .toList();
    }

    @Transactional
    public AuthResponse register(RegisterRequest req) {
        String email = req.getEmail().trim().toLowerCase();
        if (users.existsByEmail(email)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "El correo ya está registrado");
        }
        String role = normalizeRole(req.getRole());
        User user = new User();
        user.setId(UUID.randomUUID().toString());
        user.setName(TextUtils.capitalizeFirst(req.getName().trim()));
        user.setEmail(email);
        user.setPhone(req.getPhone());
        user.setPasswordHash(passwordEncoder.encode(req.getPassword()));
        user.setAuthProvider("LOCAL");
        user.setRole(role);
        user.setDepartment(TextUtils.capitalizeFirst(req.getDepartment()));
        user.setMunicipality(TextUtils.capitalizeFirst(req.getMunicipality()));
        user.setAddress(TextUtils.capitalizeFirst(req.getAddress()));
        user.setVerified(true);
        user.setVerificationStatus("VERIFIED");
        user.setBalance(WalletService.INITIAL_BALANCE);
        users.save(user);
        return tokensFor(user);
    }

    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest req) {
        String email = req.getEmail().trim().toLowerCase();
        User user = users.findByEmail(email).orElseThrow(() ->
            new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Correo o contraseña incorrectos"));
        if (user.getPasswordHash() == null
            || !passwordEncoder.matches(req.getPassword(), user.getPasswordHash())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Correo o contraseña incorrectos");
        }
        return tokensFor(user);
    }

    @Transactional
    public AuthResponse loginWithGoogle(GoogleLoginRequest req) {
        GoogleIdToken.Payload payload = verifyGoogleToken(req.getIdToken());
        String email = payload.getEmail();
        if (email == null || !Boolean.TRUE.equals(payload.getEmailVerified())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Cuenta de Google sin correo verificado");
        }
        email = email.trim().toLowerCase();
        User user = users.findByEmail(email).orElse(null);
        if (user == null) {
            String role = (req.getRole() == null || req.getRole().isBlank()) ? "PROFESSIONAL" : normalizeRole(req.getRole());
            user = new User();
            user.setId(UUID.randomUUID().toString());
            user.setName(firstNonBlank(
                (String) payload.get("name"),
                email.substring(0, email.indexOf('@'))));
            user.setEmail(email);
            user.setAuthProvider("GOOGLE");
            user.setRole(role);
            user.setProfilePhotoUrl((String) payload.get("picture"));
            user.setVerified(true);
            user.setVerificationStatus("VERIFIED");
            user.setBalance(WalletService.INITIAL_BALANCE);
            users.save(user);
        } else if (!"GOOGLE".equals(user.getAuthProvider())) {
            user.setAuthProvider("GOOGLE");
            users.save(user);
        }
        return tokensFor(user);
    }

    @Transactional(readOnly = true)
    public AuthResponse refresh(String refreshToken) {
        Claims claims;
        try {
            claims = jwtService.parseValidRefreshToken(refreshToken);
        } catch (Exception ex) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Refresh token inválido o expirado");
        }
        User user = users.findById(claims.getSubject()).orElseThrow(() ->
            new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Usuario no existe"));
        return tokensFor(user);
    }

    private AuthResponse tokensFor(User user) {
        return new AuthResponse(
            jwtService.generateAccessToken(user.getId(), user.getEmail(), user.getRole()),
            jwtService.generateRefreshToken(user.getId()),
            "Bearer",
            jwtService.getAccessExpiresInSeconds(),
            UserDto.from(user)
        );
    }

    private GoogleIdToken.Payload verifyGoogleToken(String idToken) {
        try {
            GoogleIdTokenVerifier verifier = new GoogleIdTokenVerifier.Builder(
                new NetHttpTransport(), GsonFactory.getDefaultInstance())
                .setAudience(googleClientIds)
                .build();
            GoogleIdToken token = verifier.verify(idToken);
            if (token == null) {
                throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Token de Google inválido");
            }
            return token.getPayload();
        } catch (ResponseStatusException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "No se pudo verificar el token de Google");
        }
    }

    private String normalizeRole(String role) {
        String upper = role == null ? "" : role.trim().toUpperCase();
        if (!VALID_ROLES.contains(upper)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Rol inválido (CLIENT, PROFESSIONAL o BOTH)");
        }
        return upper;
    }

    private String firstNonBlank(String... values) {
        for (String v : values) {
            if (v != null && !v.isBlank()) {
                return v;
            }
        }
        return "";
    }
}

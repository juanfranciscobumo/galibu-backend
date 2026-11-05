package com.galibu.backend.auth;

import com.galibu.backend.auth.dto.AuthResponse;
import com.galibu.backend.auth.dto.ForgotPasswordRequest;
import com.galibu.backend.auth.dto.GoogleLoginRequest;
import com.galibu.backend.auth.dto.LoginRequest;
import com.galibu.backend.auth.dto.RefreshRequest;
import com.galibu.backend.auth.dto.RegisterRequest;
import com.galibu.backend.auth.dto.ResetPasswordRequest;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private static final Logger log = LoggerFactory.getLogger(AuthController.class);

    private final AuthService authService;
    private final PasswordResetService passwordResetService;

    public AuthController(AuthService authService, PasswordResetService passwordResetService) {
        this.authService = authService;
        this.passwordResetService = passwordResetService;
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public AuthResponse register(@Valid @RequestBody RegisterRequest request) {
        log.info("auth register");
        try {
            authService.register(request);
            log.info("auth register success");
        } catch (Exception ex) {
            log.warn("auth register failed error={}", ex.getMessage());
            throw ex;
        }
        return null;
    }

    @PostMapping("/login")
    public AuthResponse login(@Valid @RequestBody LoginRequest request) {
        log.info("auth login");
        try {
            authService.login(request);
            log.info("auth login success");
        } catch (Exception ex) {
            log.warn("auth login failed error={}", ex.getMessage());
            throw ex;
        }
        return null;
    }

    @PostMapping("/google")
    public AuthResponse loginWithGoogle(@Valid @RequestBody GoogleLoginRequest request) {
        log.info("auth loginWithGoogle");
        try {
            authService.loginWithGoogle(request);
            log.info("auth loginWithGoogle success");
        } catch (Exception ex) {
            log.warn("auth loginWithGoogle failed error={}", ex.getMessage());
            throw ex;
        }
        return null;
    }

    @PostMapping("/refresh")
    public AuthResponse refresh(@Valid @RequestBody RefreshRequest request) {
        log.debug("auth refresh token={}", request.refreshToken != null ? "present" : "absent");
        return authService.refresh(request.refreshToken);
    }

    @PostMapping("/forgot-password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void forgotPassword(@Valid @RequestBody ForgotPasswordRequest request) {
        log.info("auth forgotPassword email={}", request.email);
        passwordResetService.requestReset(request.email);
        log.info("auth forgotPassword sent email={}", request.email);
    }

    @PostMapping("/reset-password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        log.info("auth resetPassword email={}", request.email);
        passwordResetService.resetPassword(request.email, request.code, request.newPassword);
        log.info("auth resetPassword success email={}", request.email);
    }
}
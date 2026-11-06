package com.galibu.backend.auth.dto;

import jakarta.validation.constraints.NotBlank;

public class GoogleLoginRequest {

    @NotBlank
    public String idToken;

    public String role;

    /**
     * Intención de la llamada: LOGIN (por defecto) solo autentica cuentas
     * existentes; REGISTER permite crear la cuenta si el correo no existe.
     */
    public String mode;
}
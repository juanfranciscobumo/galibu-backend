package com.galibu.backend.auth.dto;

import jakarta.validation.constraints.NotBlank;

public class GoogleLoginRequest {

    @NotBlank
    public String idToken;

    public String role;
}
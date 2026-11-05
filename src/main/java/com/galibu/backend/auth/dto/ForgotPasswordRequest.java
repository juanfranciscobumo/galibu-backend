package com.galibu.backend.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public class ForgotPasswordRequest {

    @NotBlank
    @Email
    public String email;

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
}
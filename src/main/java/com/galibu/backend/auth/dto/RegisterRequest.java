package com.galibu.backend.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class RegisterRequest {

    @NotBlank
    @Size(max = 120)
    public String name;

    @NotBlank
    @Email
    @Size(max = 160)
    public String email;

    @Size(max = 40)
    public String phone;

    @NotBlank
    @Size(min = 8, max = 72)
    public String password;

    /**
     * Intención de registro: CLIENT, PROFESSIONAL o BOTH. Es opcional; si no
     * llega, la cuenta nace como CLIENT (default seguro, nunca BOTH).
     */
    public String role;

    @Size(max = 80)
    public String department;

    @Size(max = 80)
    public String municipality;

    @Size(max = 255)
    public String address;
}


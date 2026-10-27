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
    private String name;

    @NotBlank
    @Email
    @Size(max = 160)
    private String email;

    @Size(max = 40)
    private String phone;

    @NotBlank
    @Size(min = 8, max = 72)
    private String password;

    @NotBlank
    private String role;

    @Size(max = 80)
    private String department;

    @Size(max = 80)
    private String municipality;

    @Size(max = 255)
    private String address;
}

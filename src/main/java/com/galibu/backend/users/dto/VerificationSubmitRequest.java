package com.galibu.backend.users.dto;

import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class VerificationSubmitRequest {
    @Size(max = 60)
    private String documentType;
    private String documentPhotoUrl;
}

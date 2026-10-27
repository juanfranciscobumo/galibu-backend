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
public class UpdateProfileRequest {

    @Size(max = 120)
    private String name;

    @Size(max = 40)
    private String phone;

    private String profilePhotoUrl;

    @Size(max = 80)
    private String department;

    @Size(max = 80)
    private String municipality;

    @Size(max = 255)
    private String address;

    @Size(max = 80)
    private String workCategory;

    private Double hourlyRate;

    private String workPhotos;

    @Size(max = 600)
    private String workDescription;

    @Size(max = 60)
    private String documentType;

    private String documentPhotoUrl;
}

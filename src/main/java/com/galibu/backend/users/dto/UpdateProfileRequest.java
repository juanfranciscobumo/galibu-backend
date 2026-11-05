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
    public String name;

    @Size(max = 40)
    public String phone;

    public String profilePhotoUrl;

    @Size(max = 80)
    public String department;

    @Size(max = 80)
    public String municipality;

    @Size(max = 255)
    public String address;

    @Size(max = 80)
    public String workCategory;

    public Double hourlyRate;

    public String workPhotos;

    @Size(max = 600)
    public String workDescription;
}


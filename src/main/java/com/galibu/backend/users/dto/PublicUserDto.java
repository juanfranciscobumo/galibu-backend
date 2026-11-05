package com.galibu.backend.users.dto;

import com.galibu.backend.users.User;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Vista pública de un profesional para el directorio. Excluye email, teléfono,
 * dirección y saldo para no filtrar datos personales a otros usuarios.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class PublicUserDto {

    public String id;
    public String name;
    public String role;
    public boolean verified;
    public float rating;
    public int completedJobs;
    public String workCategory;
    public String verificationStatus;
    public String profilePhotoUrl;
    public String department;
    public String municipality;
    public Double hourlyRate;
    public String workPhotos;
    public String workDescription;

    public static PublicUserDto from(User user) {
        PublicUserDto dto = new PublicUserDto();
        dto.setId(user.getId());
        dto.setName(user.getName() == null ? "" : user.getName());
        dto.setRole(user.getRole());
        dto.setVerified(user.isVerified());
        dto.setRating(user.getRating());
        dto.setCompletedJobs(user.getCompletedJobs());
        dto.setWorkCategory(user.getWorkCategory());
        dto.setVerificationStatus(user.getVerificationStatus());
        dto.setProfilePhotoUrl(user.getProfilePhotoUrl());
        dto.setDepartment(user.getDepartment());
        dto.setMunicipality(user.getMunicipality());
        dto.setHourlyRate(user.getHourlyRate());
        dto.setWorkPhotos(user.getWorkPhotos());
        dto.setWorkDescription(user.getWorkDescription());
        return dto;
    }
}


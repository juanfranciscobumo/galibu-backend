package com.galibu.backend.auth.dto;

import com.galibu.backend.users.User;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class UserDto {

    private String id;
    private String name;
    private String email;
    private String phone;
    private String role;
    private boolean verified;
    private float rating;
    private int completedJobs;
    private double balance;
    private String workCategory;
    private String verificationStatus;
    private String profilePhotoUrl;
    private String department;
    private String municipality;
    private String address;
    private Double hourlyRate;
    private String workPhotos;
    private String workDescription;

    public static UserDto from(User user) {
        UserDto dto = new UserDto();
        dto.setId(user.getId());
        dto.setName(user.getName() == null ? "" : user.getName());
        dto.setEmail(user.getEmail());
        dto.setPhone(user.getPhone() == null ? "" : user.getPhone());
        dto.setRole(user.getRole());
        dto.setVerified(user.isVerified());
        dto.setRating(user.getRating());
        dto.setCompletedJobs(user.getCompletedJobs());
        dto.setBalance(user.getBalance());
        dto.setWorkCategory(user.getWorkCategory());
        dto.setVerificationStatus(user.getVerificationStatus());
        dto.setProfilePhotoUrl(user.getProfilePhotoUrl());
        dto.setDepartment(user.getDepartment());
        dto.setMunicipality(user.getMunicipality());
        dto.setAddress(user.getAddress());
        dto.setHourlyRate(user.getHourlyRate());
        dto.setWorkPhotos(user.getWorkPhotos());
        dto.setWorkDescription(user.getWorkDescription());
        return dto;
    }
}

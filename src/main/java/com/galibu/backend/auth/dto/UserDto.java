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

    public String id;
    public String name;
    public String email;
    public String phone;
    public String role;
    public String authProvider;
    public boolean verified;
    public float rating;
    public int completedJobs;
    public double balance;
    public int freeAcceptancesRemaining;
    public String workCategory;
    public String verificationStatus;
    public String profilePhotoUrl;
    public String department;
    public String municipality;
    public String address;
    public Double hourlyRate;
    public String workPhotos;
    public String workDescription;

    public static UserDto from(User user) {
        UserDto dto = new UserDto();
        dto.setId(user.getId());
        dto.setName(user.getName() == null ? "" : user.getName());
        dto.setEmail(user.getEmail());
        dto.setPhone(user.getPhone() == null ? "" : user.getPhone());
        dto.setRole(user.getRole());
        dto.setAuthProvider(user.getAuthProvider());
        dto.setVerified(user.isVerified());
        dto.setRating(user.getRating());
        dto.setCompletedJobs(user.getCompletedJobs());
        dto.setBalance(user.getBalance());
        dto.setFreeAcceptancesRemaining(user.getFreeAcceptancesRemaining());
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


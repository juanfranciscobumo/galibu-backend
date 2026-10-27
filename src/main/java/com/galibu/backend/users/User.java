package com.galibu.backend.users;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "users")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class User {

    @Id
    @Column(length = 64)
    private String id;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(nullable = false, length = 160, unique = true)
    private String email;

    @Column(length = 40)
    private String phone;

    @Column(nullable = false, length = 20)
    private String role;

    @Column(name = "is_verified", nullable = false)
    private boolean verified = true;

    @Column(nullable = false)
    private float rating = 5.0f;

    @Column(name = "completed_jobs", nullable = false)
    private int completedJobs = 0;

    @Column(nullable = false)
    private double balance = 0.0;

    @Column(name = "document_type", length = 60)
    private String documentType;

    @Column(name = "document_photo_url", columnDefinition = "TEXT")
    private String documentPhotoUrl;

    @Column(name = "work_category", length = 80)
    private String workCategory;

    @Column(name = "verification_status", nullable = false, length = 20)
    private String verificationStatus = "VERIFIED";

    @Column(name = "password_hash", length = 100)
    private String passwordHash;

    @Column(name = "auth_provider", nullable = false, length = 10)
    private String authProvider = "LOCAL";

    @Column(name = "profile_photo_url", columnDefinition = "TEXT")
    private String profilePhotoUrl;

    @Column(length = 80)
    private String department;

    @Column(length = 80)
    private String municipality;

    @Column(length = 255)
    private String address;

    @Column(name = "work_photos", columnDefinition = "TEXT")
    private String workPhotos;

    @Column(name = "work_description", columnDefinition = "TEXT")
    private String workDescription;

    @Column(name = "hourly_rate")
    private Double hourlyRate;

    @Column(name = "accepted_terms", nullable = false)
    private boolean acceptedTerms = false;

    @Column(name = "created_at", nullable = false)
    private long createdAt;

    @Column(name = "updated_at", nullable = false)
    private long updatedAt;

    @PrePersist
    void onCreate() {
        long now = System.currentTimeMillis();
        if (createdAt == 0) {
            createdAt = now;
        }
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = System.currentTimeMillis();
    }
}

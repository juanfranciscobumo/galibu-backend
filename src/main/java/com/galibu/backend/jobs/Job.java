package com.galibu.backend.jobs;

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
@Table(name = "jobs")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Job {

    @Id
    @Column(length = 64)
    private String id;

    @Column(nullable = false, length = 80)
    private String category = "";

    @Column(nullable = false, length = 200)
    private String title = "";

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "budget_min", nullable = false)
    private double budgetMin = 0.0;

    @Column(length = 255)
    private String address;

    @Column(nullable = false)
    private double latitude = 0.0;

    @Column(nullable = false)
    private double longitude = 0.0;

    @Column(nullable = false, length = 20)
    private String status = "OPEN";

    @Column(name = "client_id", nullable = false, length = 64)
    private String clientId;

    @Column(name = "client_name", nullable = false, length = 120)
    private String clientName = "";

    @Column(name = "professional_id", length = 64)
    private String professionalId;

    @Column(name = "professional_name", length = 120)
    private String professionalName;

    @Column(name = "final_price")
    private Double finalPrice;

    @Column(name = "created_at", nullable = false)
    private long createdAt;

    @Column(name = "completion_rating")
    private Float completionRating;

    @Column(name = "completion_review", columnDefinition = "TEXT")
    private String completionReview;

    @Column(name = "client_rating_of_pro")
    private Float clientRatingOfPro;

    @Column(name = "client_review_of_pro", columnDefinition = "TEXT")
    private String clientReviewOfPro;

    @Column(name = "pro_rating_of_client")
    private Float proRatingOfClient;

    @Column(name = "pro_review_of_client", columnDefinition = "TEXT")
    private String proReviewOfClient;

    @Column(length = 80)
    private String department;

    @Column(length = 80)
    private String municipality;

    @Column(name = "pro_hourly_rate")
    private Double proHourlyRate;

    @Column(name = "suggested_price")
    private Double suggestedPrice;

    @Column(name = "counter_offer_amount")
    private Double counterOfferAmount;

    @Column(name = "counter_offer_sender", length = 20)
    private String counterOfferSender;

    @Column(name = "source", length = 20)
    private String source;

    @Column(name = "photos", columnDefinition = "TEXT")
    private String photos;

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

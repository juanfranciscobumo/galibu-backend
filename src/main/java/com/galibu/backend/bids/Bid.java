package com.galibu.backend.bids;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "bids")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Bid {

    @Id
    @Column(length = 128)
    private String id;

    @Column(name = "job_id", nullable = false, length = 64)
    private String jobId;

    @Column(name = "professional_id", nullable = false, length = 64)
    private String professionalId;

    @Column(name = "professional_name", nullable = false, length = 120)
    private String professionalName = "";

    @Column(name = "professional_rating", nullable = false)
    private float professionalRating = 5.0f;

    @Column(name = "professional_photo", columnDefinition = "TEXT")
    private String professionalPhoto;

    @Column(nullable = false)
    private double amount = 0.0;

    @Column(columnDefinition = "TEXT")
    private String comment;

    @Column(name = "duration_hours", nullable = false)
    private int durationHours = 1;

    @Column(nullable = false, length = 30)
    private String status = "PENDING";

    @Column(name = "last_counter_offer_amount")
    private Double lastCounterOfferAmount;

    @Column(name = "suggested_price")
    private Double suggestedPrice;

    @Column(name = "created_at", nullable = false)
    private long createdAt;

    @PrePersist
    void onCreate() {
        if (createdAt == 0) {
            createdAt = System.currentTimeMillis();
        }
    }
}

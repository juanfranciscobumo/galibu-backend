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

    /** ESTIMATED (estimada), AFTER_VISIT (tras visita) o FIXED (cerrada). */
    @Column(name = "quote_type", nullable = false, length = 20)
    private String quoteType = "ESTIMATED";

    @Column(name = "visit_required", nullable = false)
    private boolean visitRequired = false;

    @Column(name = "visit_fee")
    private Double visitFee;

    @Column(name = "materials_included", nullable = false)
    private boolean materialsIncluded = false;

    @Column(name = "materials_cost")
    private Double materialsCost;

    /** Fecha/hora propuesta para la visita tecnica (epoch millis), opcional. */
    @Column(name = "visit_scheduled_at")
    private Long visitScheduledAt;

    /** Ronda de negociacion: 0 en la primera oferta, +1 por cada contraoferta. */
    @Column(name = "counter_round", nullable = false)
    private int counterRound = 0;

    @Column(name = "created_at", nullable = false)
    private long createdAt;

    @PrePersist
    void onCreate() {
        if (createdAt == 0) {
            createdAt = System.currentTimeMillis();
        }
    }
}

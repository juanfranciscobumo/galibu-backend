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

/**
 * Movimiento del historial de una oferta: oferta inicial, contraoferta,
 * aceptacion o rechazo. Permite mostrar las rondas de negociacion sin
 * exponer ofertas de otros profesionales en el chat del job.
 */
@Entity
@Table(name = "bid_events")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class BidEvent {

    @Id
    @Column(length = 180)
    private String id;

    @Column(name = "bid_id", nullable = false, length = 128)
    private String bidId;

    @Column(name = "job_id", nullable = false, length = 64)
    private String jobId;

    @Column(name = "author_id", nullable = false, length = 64)
    private String authorId;

    /** CLIENT o PROFESSIONAL. */
    @Column(name = "author_role", nullable = false, length = 20)
    private String authorRole;

    /** OFFER, COUNTER, ACCEPT o REJECT. */
    @Column(nullable = false, length = 20)
    private String action;

    @Column(nullable = false)
    private double amount;

    @Column(name = "round_number", nullable = false)
    private int round;

    @Column(length = 2000)
    private String note;

    @Column(name = "created_at", nullable = false)
    private long createdAt;

    @PrePersist
    void onCreate() {
        if (createdAt == 0) {
            createdAt = System.currentTimeMillis();
        }
    }
}

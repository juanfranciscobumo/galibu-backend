package com.galibu.backend.subscription;

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

/**
 * Historial de suscripciones Pro. La fuente de verdad del acceso vigente es
 * {@link com.galibu.backend.users.User#getSubscriptionExpiresAt()}, pero cada
 * pago confirmado deja aquí su registro para auditoría y soporte.
 */
@Entity
@Table(name = "subscriptions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Subscription {

    @Id
    @Column(length = 64)
    private String id;

    @Column(name = "user_id", nullable = false, length = 64)
    private String userId;

    /** MONTHLY o YEARLY. */
    @Column(nullable = false, length = 20)
    private String plan;

    /** ACTIVE, CANCELLED o EXPIRED. */
    @Column(nullable = false, length = 20)
    private String status = "ACTIVE";

    @Column(nullable = false)
    private double amount;

    @Column(name = "started_at", nullable = false)
    private long startedAt;

    @Column(name = "expires_at", nullable = false)
    private long expiresAt;

    @Column(name = "bold_order_id", length = 64)
    private String boldOrderId;

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

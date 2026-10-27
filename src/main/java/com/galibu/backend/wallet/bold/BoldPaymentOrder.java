package com.galibu.backend.wallet.bold;

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
 * Orden de recarga creada contra Bold. Se guarda antes de abrir el checkout y
 * se actualiza cuando llega el pago (consulta de estado o webhook).
 */
@Entity
@Table(name = "bold_payment_orders")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class BoldPaymentOrder {

    @Id
    @Column(length = 64)
    private String id;

    @Column(name = "user_id", nullable = false, length = 64)
    private String userId;

    @Column(nullable = false)
    private double amount;

    @Column(name = "payment_link", length = 128)
    private String paymentLink;

    @Column(name = "checkout_url", length = 512)
    private String checkoutUrl;

    /** PENDING, PAID, REJECTED, EXPIRED o CANCELLED. */
    @Column(nullable = false, length = 20)
    private String status = "PENDING";

    @Column(name = "transaction_id", length = 128)
    private String transactionId;

    @Column(name = "payment_method", length = 64)
    private String paymentMethod;

    @Column(name = "created_at", nullable = false)
    private long createdAt;

    @Column(name = "updated_at", nullable = false)
    private long updatedAt;

    @Column(name = "paid_at")
    private Long paidAt;

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

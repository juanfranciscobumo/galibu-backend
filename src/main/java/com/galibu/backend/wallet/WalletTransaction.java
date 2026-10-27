package com.galibu.backend.wallet;

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
@Table(name = "wallet_transactions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class WalletTransaction {

    @Id
    @Column(length = 64)
    private String id;

    @Column(name = "user_id", nullable = false, length = 64)
    private String userId;

    @Column(nullable = false, length = 20)
    private String type;

    @Column(nullable = false)
    private double amount;

    @Column(name = "balance_after", nullable = false)
    private double balanceAfter;

    @Column(name = "job_id", length = 64)
    private String jobId;

    @Column(length = 255)
    private String description;

    @Column(name = "created_at", nullable = false)
    private long createdAt;

    @PrePersist
    void onCreate() {
        if (createdAt == 0) {
            createdAt = System.currentTimeMillis();
        }
    }
}

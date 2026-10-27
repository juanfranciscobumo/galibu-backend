package com.galibu.backend.wallet.bold;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface BoldPaymentOrderRepository extends JpaRepository<BoldPaymentOrder, String> {

    Optional<BoldPaymentOrder> findByIdAndUserId(String id, String userId);

    Optional<BoldPaymentOrder> findByPaymentLink(String paymentLink);

    java.util.List<BoldPaymentOrder> findByUserIdOrderByCreatedAtDesc(String userId);

    /**
     * Bloquea la orden para que la consulta de estado y el webhook no acrediten
     * la recarga dos veces si llegan al mismo tiempo.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from BoldPaymentOrder o where o.id = :id")
    Optional<BoldPaymentOrder> findByIdForUpdate(@Param("id") String id);
}

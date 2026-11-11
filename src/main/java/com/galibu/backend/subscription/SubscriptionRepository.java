package com.galibu.backend.subscription;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SubscriptionRepository extends JpaRepository<Subscription, String> {

    Optional<Subscription> findFirstByUserIdOrderByCreatedAtDesc(String userId);

    Optional<Subscription> findByBoldOrderId(String boldOrderId);
}

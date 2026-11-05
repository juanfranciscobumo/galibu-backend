package com.galibu.backend.auth;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetToken, String> {

    Optional<PasswordResetToken> findTopByUserIdAndUsedFalseAndExpiresAtGreaterThanOrderByCreatedAtDesc(
        String userId, long now);

    @Modifying
    @Query("update PasswordResetToken t set t.used = true where t.userId = :userId and t.used = false")
    void invalidateActiveForUser(String userId);
}

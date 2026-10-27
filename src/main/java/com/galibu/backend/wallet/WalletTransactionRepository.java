package com.galibu.backend.wallet;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface WalletTransactionRepository extends JpaRepository<WalletTransaction, String> {

    List<WalletTransaction> findByUserIdOrderByCreatedAtDesc(String userId);

    /** Borra el historial de la billetera al eliminar la cuenta del usuario. */
    @Modifying
    @Query("delete from WalletTransaction t where t.userId = :uid")
    int deleteAllForUser(@Param("uid") String uid);
}

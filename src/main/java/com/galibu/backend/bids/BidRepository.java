package com.galibu.backend.bids;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface BidRepository extends JpaRepository<Bid, String> {

    /** Ofertas que el profesional dejó en trabajos de otros clientes al eliminar su cuenta. */
    @Modifying
    @Query("delete from Bid b where b.professionalId = :uid")
    int deleteAllForUser(@Param("uid") String uid);

    List<Bid> findByJobIdOrderByCreatedAtDesc(String jobId);

    List<Bid> findByProfessionalId(String professionalId);

    java.util.Optional<Bid> findByJobIdAndProfessionalId(String jobId, String professionalId);

    List<Bid> findAllByJobIdAndProfessionalId(String jobId, String professionalId);
}

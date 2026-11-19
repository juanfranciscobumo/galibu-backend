package com.galibu.backend.jobs;

import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface JobRepository extends JpaRepository<Job, String> {

    List<Job> findByClientIdOrderByCreatedAtDesc(String clientId);

    List<Job> findByProfessionalIdOrderByCreatedAtDesc(String professionalId);

    List<Job> findByStatusOrderByCreatedAtDesc(String status);

    List<Job> findByStatusInOrderByCreatedAtDesc(Collection<String> statuses);

    List<Job> findByParentJobIdOrderByCreatedAtDesc(String parentJobId);

    List<Job> findByParentJobIdAndProfessionalId(String parentJobId, String professionalId);

    boolean existsByParentJobId(String parentJobId);

    /**
     * Servicios que consumen la cuota gratuita del profesional. Se cuentan los
     * trabajos que llegaron a estar aceptados, no solo los completados, para que
     * nadie acumule ofertas aceptadas sin plan.
     */
    @Query("select count(j) from Job j where j.professionalId = :uid and j.status in :statuses")
    long countServicesByProfessionalId(@Param("uid") String uid,
        @Param("statuses") Collection<String> statuses);

    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @Query("select j from Job j where j.id = :id")
    java.util.Optional<Job> findByIdForUpdate(@Param("id") String id);

    List<Job> findByClientIdOrProfessionalIdOrderByCreatedAtDesc(String clientId, String professionalId);

    /**
     * Trabajos en curso del usuario (como cliente o como profesional). Consulta explícita
     * porque en un método derivado el OR se evaluaría antes que el AND del estado.
     */
    @Query("select count(j) from Job j where (j.clientId = :uid or j.professionalId = :uid) "
        + "and j.status in :statuses")
    long countActiveForUser(@Param("uid") String uid, @Param("statuses") Collection<String> statuses);

    /**
     * Borra todo el historial de trabajos del usuario. Al ser SQL directo, la base
     * aplica en cascada las ofertas (bids) y las calificaciones (ratings).
     */
    @Modifying
    @Query("delete from Job j where j.clientId = :uid or j.professionalId = :uid")
    int deleteAllForUser(@Param("uid") String uid);
}

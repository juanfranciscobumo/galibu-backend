package com.galibu.backend.ratings;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RatingRepository extends JpaRepository<Rating, Long> {

    /** Calificaciones dadas o recibidas por el usuario al eliminar su cuenta. */
    @Modifying
    @Query("delete from Rating r where r.raterId = :uid or r.ratedId = :uid")
    int deleteAllForUser(@Param("uid") String uid);

    boolean existsByJobIdAndRaterId(String jobId, String raterId);

    List<Rating> findByJobIdOrderByCreatedAtDesc(String jobId);

    List<Rating> findByRatedIdOrderByCreatedAtDesc(String ratedId);
}

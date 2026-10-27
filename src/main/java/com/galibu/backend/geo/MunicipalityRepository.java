package com.galibu.backend.geo;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MunicipalityRepository extends JpaRepository<Municipality, Long> {
    List<Municipality> findByDepartmentIdOrderByNameAsc(Long departmentId);

    boolean existsByDepartmentId(Long departmentId);
}

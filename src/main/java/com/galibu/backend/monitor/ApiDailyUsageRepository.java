package com.galibu.backend.monitor;

import java.time.LocalDate;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ApiDailyUsageRepository extends JpaRepository<ApiDailyUsage, LocalDate> {

    List<ApiDailyUsage> findTop14ByOrderByUsageDateDesc();
}

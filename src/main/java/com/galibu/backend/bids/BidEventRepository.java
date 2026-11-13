package com.galibu.backend.bids;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BidEventRepository extends JpaRepository<BidEvent, String> {

    List<BidEvent> findByBidIdOrderByCreatedAtAsc(String bidId);
}

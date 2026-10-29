package com.galibu.backend.chat;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ChatMessageRepository extends JpaRepository<ChatMessage, String> {

    List<ChatMessage> findByJobIdOrderByTimestampAsc(String jobId);

    @Modifying
    @Query("update ChatMessage c set c.read = true where c.jobId = :jobId and c.senderId <> :uid")
    int markRead(@Param("jobId") String jobId, @Param("uid") String uid);
}

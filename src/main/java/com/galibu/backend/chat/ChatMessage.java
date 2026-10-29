package com.galibu.backend.chat;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "chat_messages")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ChatMessage {

    @Id
    @Column(length = 64)
    private String id;

    @Column(name = "job_id", nullable = false, length = 64)
    private String jobId;

    @Column(name = "sender_id", nullable = false, length = 64)
    private String senderId;

    @Column(name = "sender_name", nullable = false, length = 120)
    private String senderName = "";

    @Column(columnDefinition = "TEXT")
    private String text;

    @Column(nullable = false)
    private long timestamp;

    @Column(nullable = false, length = 20)
    private String type = "text";

    @Column(name = "is_read", nullable = false)
    private boolean read = false;

    @PrePersist
    void onCreate() {
        if (id == null || id.isBlank()) {
            id = UUID.randomUUID().toString();
        }
        if (timestamp == 0) {
            timestamp = System.currentTimeMillis();
        }
    }
}

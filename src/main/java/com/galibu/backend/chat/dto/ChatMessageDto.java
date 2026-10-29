package com.galibu.backend.chat.dto;

import com.galibu.backend.chat.ChatMessage;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ChatMessageDto {

    private String id;
    private String jobId;
    private String senderId;
    private String senderName;
    private String text;
    private long timestamp;
    private String type;
    private boolean read;

    public static ChatMessageDto from(ChatMessage m) {
        ChatMessageDto dto = new ChatMessageDto();
        dto.setId(m.getId());
        dto.setJobId(m.getJobId());
        dto.setSenderId(m.getSenderId());
        dto.setSenderName(m.getSenderName());
        dto.setText(m.getText());
        dto.setTimestamp(m.getTimestamp());
        dto.setType(m.getType());
        dto.setRead(m.isRead());
        return dto;
    }
}

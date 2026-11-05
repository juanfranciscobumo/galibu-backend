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

    public String id;
    public String jobId;
    public String senderId;
    public String senderName;
    public String text;
    public long timestamp;
    public String type;
    public boolean read;

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


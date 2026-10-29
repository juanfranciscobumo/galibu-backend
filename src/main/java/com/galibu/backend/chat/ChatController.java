package com.galibu.backend.chat;

import com.galibu.backend.chat.dto.ChatMessageDto;
import com.galibu.backend.chat.dto.CreateChatMessageRequest;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class ChatController {

    private final ChatService service;

    public ChatController(ChatService service) {
        this.service = service;
    }

    @GetMapping("/jobs/{jobId}/chats")
    public List<ChatMessageDto> list(Authentication auth, @PathVariable String jobId) {
        return service.list(auth.getName(), jobId);
    }

    @PostMapping("/jobs/{jobId}/chats")
    @ResponseStatus(HttpStatus.CREATED)
    public ChatMessageDto send(
        Authentication auth, @PathVariable String jobId, @Valid @RequestBody CreateChatMessageRequest request
    ) {
        return service.send(auth.getName(), jobId, request);
    }

    @PostMapping("/jobs/{jobId}/chats/read")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void markRead(Authentication auth, @PathVariable String jobId) {
        service.markRead(auth.getName(), jobId);
    }

    @DeleteMapping("/jobs/{jobId}/chats/{messageId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(
        Authentication auth, @PathVariable String jobId, @PathVariable String messageId
    ) {
        service.delete(auth.getName(), jobId, messageId);
    }
}

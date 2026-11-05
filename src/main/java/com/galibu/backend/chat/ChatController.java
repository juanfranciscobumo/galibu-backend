package com.galibu.backend.chat;

import com.galibu.backend.chat.dto.ChatMessageDto;
import com.galibu.backend.chat.dto.CreateChatMessageRequest;
import jakarta.validation.Valid;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

    private static final Logger log = LoggerFactory.getLogger(ChatController.class);

    private final ChatService service;

    public ChatController(ChatService service) {
        this.service = service;
    }

    @GetMapping("/jobs/{jobId}/chats")
    public List<ChatMessageDto> list(Authentication auth, @PathVariable String jobId) {
        log.debug("chats list userId={} jobId={}", auth.getName(), jobId);
        return service.list(auth.getName(), jobId);
    }

    @PostMapping("/jobs/{jobId}/chats")
    @ResponseStatus(HttpStatus.CREATED)
    public ChatMessageDto send(
        Authentication auth, @PathVariable String jobId, @Valid @RequestBody CreateChatMessageRequest request
    ) {
        log.info("chats send userId={} jobId={}", auth.getName(), jobId);
        return service.send(auth.getName(), jobId, request);
    }

    @PostMapping("/jobs/{jobId}/chats/read")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void markRead(Authentication auth, @PathVariable String jobId) {
        log.info("chats markRead userId={} jobId={}", auth.getName(), jobId);
        service.markRead(auth.getName(), jobId);
    }

    @DeleteMapping("/jobs/{jobId}/chats/{messageId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(
        Authentication auth, @PathVariable String jobId, @PathVariable String messageId
    ) {
        log.info("chats delete userId={} jobId={} messageId={}", auth.getName(), jobId, messageId);
        service.delete(auth.getName(), jobId, messageId);
    }
}
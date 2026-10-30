package com.galibu.backend.chat;

import com.galibu.backend.chat.dto.ChatMessageDto;
import com.galibu.backend.chat.dto.CreateChatMessageRequest;
import com.galibu.backend.jobs.Job;
import com.galibu.backend.jobs.JobRepository;
import com.galibu.backend.users.User;
import com.galibu.backend.users.UserRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ChatService {

    private final ChatMessageRepository messages;
    private final JobRepository jobs;
    private final UserRepository users;

    public ChatService(ChatMessageRepository messages, JobRepository jobs, UserRepository users) {
        this.messages = messages;
        this.jobs = jobs;
        this.users = users;
    }

    private Job requireParticipant(String uid, String jobId) {
        Job job = jobs.findById(jobId).orElseThrow(() ->
            new ResponseStatusException(HttpStatus.NOT_FOUND, "Solicitud no existe"));
        boolean isClient = uid.equals(job.getClientId());
        boolean isPro = job.getProfessionalId() != null && uid.equals(job.getProfessionalId());
        if (!isClient && !isPro) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "No participas en esta conversación");
        }
        return job;
    }

    @Transactional(readOnly = true)
    public List<ChatMessageDto> list(String uid, String jobId) {
        requireParticipant(uid, jobId);
        return messages.findByJobIdOrderByTimestampAsc(jobId).stream()
            .map(ChatMessageDto::from)
            .toList();
    }

    @Transactional
    public ChatMessageDto send(String uid, String jobId, CreateChatMessageRequest req) {
        requireParticipant(uid, jobId);
        User user = users.findById(uid).orElseThrow(() ->
            new ResponseStatusException(HttpStatus.NOT_FOUND, "Usuario no existe"));
        ChatMessage m = new ChatMessage();
        m.setId(UUID.randomUUID().toString());
        m.setJobId(jobId);
        m.setSenderId(uid);
        m.setSenderName(user.getName());
        m.setText(req.getText());
        m.setType(req.getType() == null || req.getType().isBlank() ? "text" : req.getType());
        m.setTimestamp(System.currentTimeMillis());
        m.setRead(false);
        return ChatMessageDto.from(messages.save(m));
    }

    /**
     * Mensaje automático del sistema. No lo envía ninguno de los participantes,
     * por eso se persiste directamente y ambos lo reciben por el polling del chat.
     */
    @Transactional
    public void postSystemMessage(String jobId, String senderName, String text) {
        ChatMessage m = new ChatMessage();
        m.setId(UUID.randomUUID().toString());
        m.setJobId(jobId);
        m.setSenderId("system");
        m.setSenderName(senderName == null || senderName.isBlank() ? "Sistema" : senderName);
        m.setText(text);
        m.setType("system");
        m.setTimestamp(System.currentTimeMillis());
        m.setRead(false);
        messages.save(m);
    }

    @Transactional
    public void markRead(String uid, String jobId) {
        requireParticipant(uid, jobId);
        messages.markRead(jobId, uid);
    }

    @Transactional
    public void delete(String uid, String jobId, String messageId) {
        requireParticipant(uid, jobId);
        ChatMessage m = messages.findById(messageId).orElseThrow(() ->
            new ResponseStatusException(HttpStatus.NOT_FOUND, "Mensaje no existe"));
        if (!m.getJobId().equals(jobId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Mensaje no existe");
        }
        // Solo el autor (o un participante) puede borrar; aquí permitimos a participantes.
        messages.delete(m);
    }
}

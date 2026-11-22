package com.galibu.backend.chat;

import com.galibu.backend.chat.dto.ChatMessageDto;
import com.galibu.backend.chat.dto.CreateChatMessageRequest;
import com.galibu.backend.jobs.Job;
import com.galibu.backend.jobs.JobRepository;
import com.galibu.backend.notifications.NotificationService;
import com.galibu.backend.users.User;
import com.galibu.backend.users.UserRepository;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ChatService {

    private final ChatMessageRepository messages;
    // Caché corto de trabajos para evitar una consulta a la BD en cada
    // operación de chat (la BD remota tiene ~300 ms de latencia).
    private final ConcurrentMap<String, Job> jobCache = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, Long> jobCacheTime = new ConcurrentHashMap<>();
    private static final long JOB_CACHE_TTL_MS = 10_000L;
    private final JobRepository jobs;
    private final UserRepository users;
    private final NotificationService notifications;

    public ChatService(ChatMessageRepository messages, JobRepository jobs, UserRepository users,
            NotificationService notifications) {
        this.messages = messages;
        this.jobs = jobs;
        this.users = users;
        this.notifications = notifications;
    }

    private Job requireParticipant(String uid, String jobId) {
        Job job = cachedJob(jobId);
        if (job == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Solicitud no existe");
        }
        boolean isClient = uid.equals(job.getClientId());
        boolean isPro = job.getProfessionalId() != null && uid.equals(job.getProfessionalId());
        if (!isClient && !isPro) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "No participas en esta conversación");
        }
        return job;
    }

    private Job cachedJob(String jobId) {
        long now = System.currentTimeMillis();
        Long cachedAt = jobCacheTime.get(jobId);
        Job cached = jobCache.get(jobId);
        if (cached != null && cachedAt != null && (now - cachedAt) < JOB_CACHE_TTL_MS) {
            return cached;
        }
        Job fresh = jobs.findById(jobId).orElse(null);
        if (fresh != null) {
            jobCache.put(jobId, fresh);
            jobCacheTime.put(jobId, now);
        } else {
            jobCache.remove(jobId);
            jobCacheTime.remove(jobId);
        }
        return fresh;
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
        Job job = requireParticipant(uid, jobId);
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
        ChatMessage saved = messages.save(m);
        // Notificar al otro participante por push (el chat también usa polling de 5 s).
        String otherId = uid.equals(job.getClientId()) ? job.getProfessionalId() : job.getClientId();
        if (otherId != null && !otherId.isBlank()) {
            String preview = req.getText() == null ? "" : req.getText();
            if (preview.length() > 80) preview = preview.substring(0, 77) + "...";
            notifications.notifyUser(otherId, "Nuevo mensaje",
                user.getName() + ": " + preview, jobId);
        }
        return ChatMessageDto.from(saved);
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

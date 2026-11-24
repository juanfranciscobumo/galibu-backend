package com.galibu.backend.jobs;

import com.galibu.backend.common.TextUtils;
import com.galibu.backend.chat.ChatService;
import com.galibu.backend.jobs.dto.CreateJobRequest;
import com.galibu.backend.jobs.dto.JobDto;
import com.galibu.backend.notifications.NotificationService;
import com.galibu.backend.subscription.SubscriptionService;
import com.galibu.backend.users.User;
import com.galibu.backend.users.UserRepository;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class JobService {

    private final JobRepository jobs;
    private final UserRepository users;
    private final com.galibu.backend.ratings.RatingService ratingService;
    private final SubscriptionService subscriptions;
    private final ChatService chat;
    private final NotificationService notifications;

    public JobService(JobRepository jobs, UserRepository users,
            com.galibu.backend.ratings.RatingService ratingService,
            SubscriptionService subscriptions, ChatService chat,
            NotificationService notifications) {
        this.jobs = jobs;
        this.users = users;
        this.ratingService = ratingService;
        this.subscriptions = subscriptions;
        this.chat = chat;
        this.notifications = notifications;
    }

    @Transactional(readOnly = true)
    public List<JobDto> list(String uid, String clientId, String professionalId, String status) {
        boolean mine = clientId == null && professionalId == null && status == null;
        if (mine) {
            return toDto(jobs.findByClientIdOrProfessionalIdOrderByCreatedAtDesc(uid, uid));
        }
        if (clientId != null && !clientId.equals(uid)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Solo puedes listar tus propias solicitudes");
        }
        if (professionalId != null && !professionalId.equals(uid)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Solo puedes listar tus propios trabajos");
        }
        if (status != null && !"OPEN".equalsIgnoreCase(status)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Solo la lista OPEN es pública");
        }
        Set<String> seen = new LinkedHashSet<>();
        List<Job> merged = new ArrayList<>();
        if (clientId != null) {
            for (Job j : jobs.findByClientIdOrderByCreatedAtDesc(clientId)) {
                if (seen.add(j.getId())) {
                    merged.add(j);
                }
            }
        }
        if (professionalId != null) {
            for (Job j : jobs.findByProfessionalIdOrderByCreatedAtDesc(professionalId)) {
                if (seen.add(j.getId())) {
                    merged.add(j);
                }
            }
        }
        if (status != null) {
            // La lista pública incluye OPEN y las negociaciones aún sin
            // profesional asignado: una oferta no debe "robarle" la solicitud
            // al resto del tablero.
            for (Job j : jobs.findByStatusInOrderByCreatedAtDesc(List.of("OPEN", "NEGOTIATING"))) {
                if (isPublic(j) && seen.add(j.getId())) {
                    merged.add(j);
                }
            }
        }
        merged.sort((a, b) -> Long.compare(b.getCreatedAt(), a.getCreatedAt()));
        return toDto(merged);
    }

    @Transactional(readOnly = true)
    public JobDto get(String uid, String jobId) {
        Job job = findOr404(jobId);
        if (isPublic(job)) {
            return JobDto.from(job);
        }
        requireParticipant(uid, job);
        return JobDto.from(job);
    }

    @Transactional
    public JobDto create(String uid, CreateJobRequest req) {
        User user = findUserOr404(uid);
        if (!"CLIENT".equals(user.getRole()) && !"BOTH".equals(user.getRole())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Solo un cliente puede crear solicitudes");
        }
        rejectDuplicateActiveRequest(uid, req);
        Job job = new Job();
        job.setId(resolveJobId(req.getId()));
        job.setCategory(TextUtils.capitalizeFirst(req.getCategory() == null ? "" : req.getCategory()));
        job.setTitle(TextUtils.capitalizeFirst(req.getTitle()));
        job.setDescription(TextUtils.capitalizeFirst(req.getDescription()));
        job.setBudgetMin(req.getBudgetMin());
        job.setSuggestedPrice(req.getSuggestedPrice());
        job.setAddress(TextUtils.capitalizeFirst(req.getAddress()));
        job.setDepartment(TextUtils.capitalizeFirst(req.getDepartment()));
        job.setMunicipality(TextUtils.capitalizeFirst(req.getMunicipality()));
        job.setLatitude(req.getLatitude());
        job.setLongitude(req.getLongitude());
        job.setStatus("OPEN");
        job.setClientId(uid);
        job.setClientName(user.getName());
        job.setProHourlyRate(req.getProHourlyRate());
        job.setSource(normalizeSource(req.getSource()));
        job.setPhotos(req.getPhotos());
        if (req.getProfessionalId() != null && !req.getProfessionalId().isBlank()) {
            if (uid.equals(req.getProfessionalId())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "No puedes asignarte a ti mismo como profesional de tu solicitud");
            }
            User pro = users.findById(req.getProfessionalId()).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "Profesional no existe"));
            if (!"PROFESSIONAL".equals(pro.getRole()) && !"BOTH".equals(pro.getRole())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El asignado no es profesional");
            }
            job.setProfessionalId(pro.getId());
            job.setProfessionalName(
                req.getProfessionalName() == null || req.getProfessionalName().isBlank()
                    ? pro.getName() : req.getProfessionalName());
            job.setStatus("NEGOTIATING");
        }
        Job saved = jobs.save(job);
        boolean directHire = saved.getProfessionalId() != null && !saved.getProfessionalId().isBlank();
        boolean chatThread = saved.getId() != null && saved.getId().startsWith("chat_");
        if (directHire) {
            chat.postSystemMessage(saved.getId(), "Sistema",
                "🤝 " + user.getName() + " te ha contratado directamente para '" + saved.getTitle()
                    + "'. Pueden acordar el precio y los detalles por el chat.");
            if (chatThread) {
                notifications.notifyUser(saved.getProfessionalId(), "Nueva consulta",
                    "Un cliente quiere hablar contigo sobre '" + saved.getCategory()
                        + "'. Responde desde Mensajes.", saved.getId());
            } else {
                notifications.notifyUser(saved.getProfessionalId(), "Nueva contratación directa",
                    "Te han contratado directamente para '" + saved.getTitle() + "'. Revisa la solicitud.", saved.getId());
                chat.invalidateJobCache(saved.getId());
            }
        } else {
            // Nueva solicitud abierta: avisar a profesionales con categoría compatible
            // (o sin categoría definida) para que la vean al instante.
            String cat = saved.getCategory() == null ? "" : saved.getCategory().trim().toLowerCase();
            for (User pro : users.findByRoleInOrderByNameAsc(List.of("PROFESSIONAL", "BOTH"))) {
                if (pro.getId().equals(saved.getClientId())) continue;
                String proCat = pro.getWorkCategory() == null ? "" : pro.getWorkCategory().trim().toLowerCase();
                boolean match = cat.isBlank() || proCat.isBlank()
                    || proCat.contains(cat) || cat.contains(proCat);
                if (match) {
                    notifications.notifyUser(pro.getId(), "Nueva solicitud disponible",
                        "Nueva solicitud: '" + saved.getTitle() + "' en " + saved.getMunicipality() + ".", saved.getId());
                }
            }
        }
        return JobDto.from(saved);
    }

    /**
     * Un profesional se postula a una solicitud publicada. Cada postulación es
     * un job hijo con su propio hilo de chat, de modo que varios profesionales
     * pueden postularse a la vez y el cliente decide con quién continuar.
     */
    @Transactional
    public JobDto apply(String uid, String jobId) {
        Job parent = findOr404(jobId);
        // Reintento sobre una postulación ya creada: responderla tal cual.
        if (parent.getParentJobId() != null && !parent.getParentJobId().isBlank()) {
            return JobDto.from(parent);
        }
        User pro = findUserOr404(uid);
        if (!"PROFESSIONAL".equals(pro.getRole()) && !"BOTH".equals(pro.getRole())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Solo un profesional puede postularse");
        }
        if (uid.equals(parent.getClientId())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "No puedes postularte a tu propia solicitud");
        }
        if (!"OPEN".equals(parent.getStatus())
                || (parent.getProfessionalId() != null && !parent.getProfessionalId().isBlank())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "La solicitud ya no está disponible");
        }

        // Idempotente: si ya existe una postulación activa, devolverla.
        Job application = null;
        for (Job existing : jobs.findByParentJobIdAndProfessionalId(parent.getId(), uid)) {
            if (!"CANCELLED".equals(existing.getStatus()) && !"COMPLETED".equals(existing.getStatus())) {
                return JobDto.from(existing);
            }
            if ("CANCELLED".equals(existing.getStatus())) {
                application = existing;
            }
        }
        boolean reactivated = application != null;
        if (application == null) {
            application = new Job();
            application.setId("app_" + UUID.randomUUID().toString().replace("-", ""));
            application.setCreatedAt(System.currentTimeMillis());
        }

        application.setParentJobId(parent.getId());
        application.setCategory(parent.getCategory());
        application.setTitle(parent.getTitle());
        application.setDescription(parent.getDescription());
        application.setBudgetMin(parent.getBudgetMin());
        application.setAddress(parent.getAddress());
        application.setLatitude(parent.getLatitude());
        application.setLongitude(parent.getLongitude());
        application.setDepartment(parent.getDepartment());
        application.setMunicipality(parent.getMunicipality());
        application.setSuggestedPrice(parent.getSuggestedPrice());
        application.setProHourlyRate(parent.getProHourlyRate());
        application.setPhotos(parent.getPhotos());
        application.setSource("APPLICATION");
        application.setClientId(parent.getClientId());
        application.setClientName(parent.getClientName());
        application.setProfessionalId(pro.getId());
        application.setProfessionalName(pro.getName());
        application.setStatus("NEGOTIATING");
        application.setCompletionRating(null);
        application.setCompletionReview(null);
        application.setClientRatingOfPro(null);
        application.setClientReviewOfPro(null);
        application.setProRatingOfClient(null);
        application.setProReviewOfClient(null);
        Job saved = jobs.save(application);

        chat.postSystemMessage(saved.getId(), "Sistema",
            (reactivated ? "📩 " + pro.getName() + " volvió a postularse. " : "📩 " + pro.getName() + " envió su postulación. ")
                + "Pueden acordar el precio y los detalles por el chat.");
        notifications.notifyUser(parent.getClientId(), "Nuevo profesional postulado",
            pro.getName() + " se postuló para '" + parent.getTitle() + "'. Revisa su mensaje en el chat.",
            saved.getId());
        chat.invalidateJobCache(saved.getId());
        chat.invalidateJobCache(parent.getId());
        return JobDto.from(saved);
    }

    /** Compatibilidad con clientes antiguos: postularse es la nueva forma de tomar. */
    @Transactional
    public JobDto acceptJob(String uid, String jobId) {
        return apply(uid, jobId);
    }

    @Transactional
    public JobDto clientAccept(String uid, String jobId) {
        Job job = findOr404(jobId);
        if (!uid.equals(job.getClientId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Solo el cliente puede aceptar al profesional");
        }
        if (!"NEGOTIATING".equals(job.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "La solicitud no está en negociación");
        }
        if (job.getProfessionalId() == null || job.getProfessionalId().isBlank()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "La solicitud aún no tiene profesional asignado");
        }
        Job parent = null;
        if (job.getParentJobId() != null && !job.getParentJobId().isBlank()) {
            parent = jobs.findById(job.getParentJobId()).orElse(null);
            if (parent != null && !"OPEN".equals(parent.getStatus())) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Ya elegiste a un profesional para esta solicitud");
            }
        }
        job.setStatus("ACCEPTED");
        Job saved = jobs.save(job);
        chat.postSystemMessage(jobId, "Sistema",
            "✅ " + job.getClientName() + " aceptó la propuesta. El profesional puede iniciar el servicio.");
        notifications.notifyUser(job.getProfessionalId(), "Propuesta aceptada",
            "El cliente aceptó tu propuesta para '" + job.getTitle() + "'. Puedes iniciar el servicio.", jobId);

        // La solicitud padre queda cubierta y las demás postulaciones se cierran.
        if (parent != null) {
            parent.setStatus("ASSIGNED");
            jobs.save(parent);
            for (Job sibling : jobs.findByParentJobIdOrderByCreatedAtDesc(parent.getId())) {
                if (sibling.getId().equals(job.getId())) continue;
                if (!"NEGOTIATING".equals(sibling.getStatus()) && !"ACCEPTED".equals(sibling.getStatus())) continue;
                sibling.setStatus("CANCELLED");
                sibling.setCompletionReview("El cliente eligió a otro profesional");
                jobs.save(sibling);
                chat.postSystemMessage(sibling.getId(), "Sistema",
                    "El cliente eligió a otro profesional para esta solicitud. La postulación quedó cerrada.");
                chat.invalidateJobCache(sibling.getId());
                if (sibling.getProfessionalId() != null && !sibling.getProfessionalId().isBlank()) {
                    notifications.notifyUser(sibling.getProfessionalId(), "Postulación no seleccionada",
                        "El cliente eligió a otro profesional para '" + sibling.getTitle() + "'.", sibling.getId());
                }
            }
        }
        if (parent != null) chat.invalidateJobCache(parent.getId());
        chat.invalidateJobCache(jobId);
        return JobDto.from(saved);
    }

    /**
     * El cliente rechaza una postulación concreta. La solicitud padre sigue
     * abierta para recibir o mantener otras postulaciones; solo se cierra esta
     * conversación (el historial queda visible para el profesional).
     */
    @Transactional
    public JobDto rejectApplication(String uid, String jobId) {
        Job job = findOr404(jobId);
        if (!uid.equals(job.getClientId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                "Solo el cliente puede rechazar una postulación");
        }
        if (job.getParentJobId() == null || job.getParentJobId().isBlank()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                "Solo se pueden rechazar postulaciones de una solicitud publicada");
        }
        if (!"NEGOTIATING".equals(job.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                "Esta postulación ya no está activa");
        }
        job.setStatus("CANCELLED");
        job.setCompletionReview("El cliente rechazó la postulación");
        Job saved = jobs.save(job);

        chat.postSystemMessage(jobId, "Sistema",
            "El cliente rechazó esta postulación. La conversación quedó cerrada.");
        if (job.getProfessionalId() != null && !job.getProfessionalId().isBlank()) {
            notifications.notifyUser(job.getProfessionalId(), "Postulación rechazada",
                "El cliente rechazó tu postulación para '" + job.getTitle() + "'.", jobId);
        }
        chat.invalidateJobCache(jobId);
        chat.invalidateJobCache(job.getParentJobId());
        return JobDto.from(saved);
    }

    @Transactional
    public JobDto cancel(String uid, String jobId, String reason) {
        Job job = findOr404(jobId);
        requireParticipant(uid, job);
        if ("COMPLETED".equals(job.getStatus()) || "CANCELLED".equals(job.getStatus())
            || "IN_PROGRESS".equals(job.getStatus()) || "EN_PROGRESO".equals(job.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "No se puede cancelar un servicio en progreso o ya cerrado");
        }
        String previousStatus = job.getStatus();
        User actor = findUserOr404(uid);
        job.setStatus("CANCELLED");
        Job saved = jobs.save(job);
        String suffix = reason == null || reason.isBlank() ? "" : ": " + reason.trim();
        chat.postSystemMessage(jobId, "Sistema",
            "⚠️ Este trabajo ha sido cancelado por " + actor.getName() + suffix + ".");
        String otherId = uid.equals(job.getClientId()) ? job.getProfessionalId() : job.getClientId();
        if (otherId != null && !otherId.isBlank()) {
            notifications.notifyUser(otherId, "Servicio cancelado",
                "La solicitud '" + job.getTitle() + "' ha sido cancelada por " + actor.getName() + ".", jobId);
        }

        if (job.getParentJobId() != null && !job.getParentJobId().isBlank()) {
            // Postulación cancelada: si aún no estaba aceptada, la solicitud
            // padre vuelve a estar abierta; si ya había sido elegida, se cierra.
            jobs.findById(job.getParentJobId()).ifPresent(parent -> {
                if ("ASSIGNED".equals(parent.getStatus())) {
                    parent.setStatus("ACCEPTED".equals(previousStatus) ? "OPEN" : "CLOSED");
                    jobs.save(parent);
                }
            });
        } else {
            // Cancelar la solicitud padre cierra todas sus postulaciones activas.
            for (Job child : jobs.findByParentJobIdOrderByCreatedAtDesc(job.getId())) {
                if (!"NEGOTIATING".equals(child.getStatus()) && !"ACCEPTED".equals(child.getStatus())
                        && !"IN_PROGRESS".equals(child.getStatus())) {
                    continue;
                }
                child.setStatus("CANCELLED");
                child.setCompletionReview("La solicitud fue cancelada");
                jobs.save(child);
                chat.postSystemMessage(child.getId(), "Sistema",
                    actor.getName() + " canceló la solicitud. La postulación quedó cerrada.");
                chat.invalidateJobCache(child.getId());
                if (child.getProfessionalId() != null && !child.getProfessionalId().isBlank()) {
                    notifications.notifyUser(child.getProfessionalId(), "Solicitud cancelada",
                        "La solicitud '" + child.getTitle() + "' fue cancelada por " + actor.getName() + ".",
                        child.getId());
                }
            }
        }
        chat.invalidateJobCache(jobId);
        if (job.getParentJobId() != null && !job.getParentJobId().isBlank()) chat.invalidateJobCache(job.getParentJobId());
        return JobDto.from(saved);
    }

    @Transactional
    public void delete(String uid, String jobId) {
        Job job = findOr404(jobId);
        if (!uid.equals(job.getClientId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Solo el cliente puede eliminar su solicitud");
        }
        if (job.getParentJobId() != null && !job.getParentJobId().isBlank()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                "Una postulación no se elimina: cancélala para cerrar la conversación.");
        }
        if (jobs.existsByParentJobId(job.getId())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                "Tienes postulaciones en esta solicitud. Cancélala para cerrar las conversaciones.");
        }
        if ("NEGOTIATING".equals(job.getStatus()) || "ACCEPTED".equals(job.getStatus())
            || "IN_PROGRESS".equals(job.getStatus()) || "COMPLETED".equals(job.getStatus())
            || "ASSIGNED".equals(job.getStatus()) || "CLOSED".equals(job.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                "No puedes eliminar una solicitud aceptada, en curso o completada. Cancelarla si ya no la necesitas.");
        }
        jobs.delete(job);
        chat.invalidateJobCache(jobId);
    }

    @Transactional
    public JobDto startService(String uid, String jobId) {
        Job job = findOr404(jobId);
        requireAssignedProfessional(uid, job);
        boolean isApplication = job.getParentJobId() != null && !job.getParentJobId().isBlank();
        if (isApplication && !"ACCEPTED".equals(job.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                "El cliente debe aceptar tu postulación antes de iniciar el servicio");
        }
        if (!"ACCEPTED".equals(job.getStatus()) && !"NEGOTIATING".equals(job.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Solo se puede iniciar un servicio aceptado o en negociación");
        }
        job.setStatus("IN_PROGRESS");
        job.setCounterOfferSender(null);
        Job saved = jobs.save(job);
        chat.postSystemMessage(jobId, "Sistema",
            "¡El profesional inició el servicio! El trabajo está en progreso.");
        chat.invalidateJobCache(jobId);
        if (job.getParentJobId() != null && !job.getParentJobId().isBlank()) chat.invalidateJobCache(job.getParentJobId());
        notifications.notifyUser(job.getClientId(), "Servicio iniciado",
            "El profesional ha comenzado el trabajo '" + job.getTitle() + "'.", jobId);
        return JobDto.from(saved);
    }

    @Transactional
    public JobDto finishService(String uid, String jobId, Integer stars, String comment) {
        Job job = findOr404(jobId);
        requireParticipant(uid, job);
        if (job.getProfessionalId() == null || job.getProfessionalId().isBlank()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "La solicitud aún no tiene profesional asignado");
        }
        // La calificación es opcional: se registra solo si llega un valor 1-5.
        boolean shouldRate = stars != null && stars >= 1 && stars <= 5;
        // El servicio se cierra una sola vez: si el otro participante ya lo
        // finalizó, este segundo cierre solo registra su calificación (sin
        // repetir mensajes de sistema ni notificaciones).
        if ("COMPLETED".equals(job.getStatus())) {
            if (shouldRate) {
                rateIgnoringDuplicates(uid, jobId, stars, comment);
            }
            return JobDto.from(findOr404(jobId));
        }
        if (!"IN_PROGRESS".equals(job.getStatus()) && !"ACCEPTED".equals(job.getStatus())
                && !"NEGOTIATING".equals(job.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                "Solo se puede finalizar un servicio en negociación, aceptado o en progreso");
        }
        job.setStatus("COMPLETED");
        job.setCounterOfferSender(null);
        jobs.save(job);
        closeParentIfApplication(job);
        chat.postSystemMessage(jobId, "Sistema",
            "¡Servicio finalizado! Ya puedes calificar el trabajo realizado.");
        notifications.notifyUser(job.getClientId(), "Servicio finalizado",
            "El trabajo '" + job.getTitle() + "' ha sido finalizado. Califica al profesional.", jobId);
        if (job.getProfessionalId() != null) {
            notifications.notifyUser(job.getProfessionalId(), "Servicio finalizado",
                "El trabajo '" + job.getTitle() + "' ha sido finalizado. Califica al cliente.", jobId);
        }
        // Calificación de quien finaliza (cliente o profesional) en la misma operación.
        if (shouldRate) {
            rateIgnoringDuplicates(uid, jobId, stars, comment);
        }
        chat.invalidateJobCache(jobId);
        if (job.getParentJobId() != null && !job.getParentJobId().isBlank()) chat.invalidateJobCache(job.getParentJobId());
        return JobDto.from(findOr404(jobId));
    }

    /**
     * Registra la calificación de quien cierra el servicio. Si ya había
     * calificado (reintento), el conflicto se ignora: la operación es idempotente.
     */
    private void rateIgnoringDuplicates(String uid, String jobId, int stars, String comment) {
        try {
            ratingService.rate(uid, jobId,
                new com.galibu.backend.ratings.dto.CreateRatingRequest(stars, comment));
        } catch (ResponseStatusException ex) {
            if (ex.getStatusCode() != HttpStatus.CONFLICT) {
                throw ex;
            }
        }
    }

    /** Cuando se completa una postulación, la solicitud padre queda cerrada. */
    private void closeParentIfApplication(Job child) {
        if (child.getParentJobId() == null || child.getParentJobId().isBlank()) {
            return;
        }
        jobs.findById(child.getParentJobId()).ifPresent(parent -> {
            if ("ASSIGNED".equals(parent.getStatus())) {
                parent.setStatus("CLOSED");
                jobs.save(parent);
            }
        });
    }

    /**
     * No se permite una solicitud activa con igual categoría y ubicación
     * (departamento + municipio + dirección, insensible a mayúsculas).
     * Otra dirección con igual categoría sí se permite. Las cerradas
     * (COMPLETED/CANCELLED) no bloquean.
     */
    private void rejectDuplicateActiveRequest(String uid, CreateJobRequest req) {
        // Los hilos de chat de consulta no son solicitudes de servicio: se
        // crean aunque exista una solicitud activa en la misma ubicación.
        if (req.getId() != null && req.getId().startsWith("chat_")) {
            return;
        }
        for (Job j : jobs.findByClientIdOrderByCreatedAtDesc(uid)) {
            // Las postulaciones heredan la ubicación del padre: no cuentan como
            // solicitudes propias para el control de duplicados.
            if (j.getParentJobId() != null && !j.getParentJobId().isBlank()) {
                continue;
            }
            if ("COMPLETED".equals(j.getStatus()) || "CANCELLED".equals(j.getStatus())
                    || "CLOSED".equals(j.getStatus())) {
                continue;
            }
            if (req.getCategory() != null && req.getCategory().equalsIgnoreCase(j.getCategory())
                    && norm(req.getDepartment()).equals(norm(j.getDepartment()))
                    && norm(req.getMunicipality()).equals(norm(j.getMunicipality()))
                    && norm(req.getAddress()).equals(norm(j.getAddress()))) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Ya tienes una solicitud activa con esta ubicación y categoría");
            }
        }
    }

    private String norm(String value) {
        return value == null ? "" : value.trim().toLowerCase();
    }

    private String normalizeSource(String value) {
        if (value == null) {
            return "PUBLISH";
        }
        String upper = value.trim().toUpperCase();
        if ("DIRECTORY".equals(upper)) {
            return "DIRECTORY";
        }
        return "PUBLISH";
    }

    private String resolveJobId(String requestedId) {        if (requestedId != null && requestedId.matches("[A-Za-z0-9_-]{1,64}")) {
            if (!jobs.existsById(requestedId)) {
                return requestedId;
            }
            throw new ResponseStatusException(HttpStatus.CONFLICT, "El ID de solicitud ya existe");
        }
        return "job_" + UUID.randomUUID().toString().replace("-", "");
    }

    private Job findOr404(String jobId) {        return jobs.findById(jobId).orElseThrow(() ->
            new ResponseStatusException(HttpStatus.NOT_FOUND, "Solicitud no existe"));
    }

    private User findUserOr404(String uid) {
        return users.findById(uid).orElseThrow(() ->
            new ResponseStatusException(HttpStatus.NOT_FOUND, "Usuario no existe"));
    }

    private boolean isPublic(Job job) {
        if ("OPEN".equals(job.getStatus())) {
            return true;
        }
        return "NEGOTIATING".equals(job.getStatus())
            && (job.getProfessionalId() == null || job.getProfessionalId().isBlank());
    }

    private void requireParticipant(String uid, Job job) {
        if (!uid.equals(job.getClientId()) && !uid.equals(job.getProfessionalId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "No participas en esta solicitud");
        }
    }

    private void requireAssignedProfessional(String uid, Job job) {
        if (job.getProfessionalId() == null || !uid.equals(job.getProfessionalId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Solo el profesional asignado puede hacerlo");
        }
    }

    private List<JobDto> toDto(List<Job> list) {
        return list.stream().map(JobDto::from).toList();
    }
}

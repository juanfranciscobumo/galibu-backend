package com.galibu.backend.jobs;

import com.galibu.backend.common.TextUtils;
import com.galibu.backend.bids.Bid;
import com.galibu.backend.bids.BidRepository;
import com.galibu.backend.jobs.dto.CounterRequest;
import com.galibu.backend.jobs.dto.CreateJobRequest;
import com.galibu.backend.jobs.dto.JobDto;
import com.galibu.backend.users.User;
import com.galibu.backend.users.UserRepository;
import com.galibu.backend.wallet.WalletService;
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
    private final BidRepository bids;
    private final com.galibu.backend.ratings.RatingService ratingService;
    private final WalletService wallet;

    public JobService(JobRepository jobs, UserRepository users, BidRepository bids,
            com.galibu.backend.ratings.RatingService ratingService, WalletService wallet) {
        this.jobs = jobs;
        this.users = users;
        this.bids = bids;
        this.ratingService = ratingService;
        this.wallet = wallet;
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
            // Negociaciones activas: NEGOTIATING sin asignar donde el profesional ya ofertó.
            // Sin esto, al contraofertar (el job deja de ser OPEN) el job desaparece del sync.
            for (Bid b : bids.findByProfessionalId(professionalId)) {
                jobs.findById(b.getJobId()).ifPresent(j -> {
                    if ("NEGOTIATING".equals(j.getStatus())
                            && (j.getProfessionalId() == null || j.getProfessionalId().isBlank())
                            && seen.add(j.getId())) {
                        merged.add(j);
                    }
                });
            }
        }
        if (status != null) {
            for (Job j : jobs.findByStatusOrderByCreatedAtDesc(status.toUpperCase())) {
                if (seen.add(j.getId())) {
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
        return JobDto.from(jobs.save(job));
    }

    @Transactional
    public JobDto cancel(String uid, String jobId) {
        Job job = findOr404(jobId);
        requireParticipant(uid, job);
        if ("COMPLETED".equals(job.getStatus()) || "CANCELLED".equals(job.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "La solicitud ya está cerrada");
        }
        job.setStatus("CANCELLED");
        return JobDto.from(jobs.save(job));
    }

    @Transactional
    public void delete(String uid, String jobId) {
        Job job = findOr404(jobId);
        if (!uid.equals(job.getClientId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Solo el cliente puede eliminar su solicitud");
        }
        jobs.delete(job);
    }

    @Transactional
    public JobDto counter(String uid, String jobId, CounterRequest req) {
        Job job = findOr404(jobId);
        requireParticipant(uid, job);
        if (!"OPEN".equals(job.getStatus()) && !"NEGOTIATING".equals(job.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "La solicitud no admite contraofertas en su estado actual");
        }
        job.setStatus("NEGOTIATING");
        job.setCounterOfferAmount(req.getAmount());
        job.setCounterOfferSender(uid.equals(job.getClientId()) ? "CLIENT" : "PROFESSIONAL");
        return JobDto.from(jobs.save(job));
    }

    @Transactional
    public JobDto acceptDirect(String uid, String jobId) {
        Job job = findOr404(jobId);
        boolean isAssignedPro = job.getProfessionalId() != null && uid.equals(job.getProfessionalId());
        // El cliente también puede aceptar, pero solo la contraoferta vigente del profesional.
        boolean isClientAcceptingProCounter = uid.equals(job.getClientId())
            && "PROFESSIONAL".equals(job.getCounterOfferSender())
            && job.getCounterOfferAmount() != null;
        if (!isAssignedPro && !isClientAcceptingProCounter) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Solo el profesional asignado puede hacerlo");
        }
        if (isAssignedPro) {
            // La tarifa de aceptación la paga el profesional (el cliente acepta gratis).
            wallet.chargeAcceptFee(uid, jobId, job.getTitle());
        }
        if ("ACCEPTED".equals(job.getStatus()) || "COMPLETED".equals(job.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "La solicitud ya fue aceptada o completada");
        }
        double price = job.getCounterOfferAmount() != null ? job.getCounterOfferAmount()
            : job.getProHourlyRate() != null ? job.getProHourlyRate() : job.getBudgetMin();
        job.setStatus("ACCEPTED");
        job.setFinalPrice(price);
        job.setCounterOfferSender(null);
        return JobDto.from(jobs.save(job));
    }

    @Transactional
    public JobDto rejectDirect(String uid, String jobId) {
        Job job = findOr404(jobId);
        requireAssignedProfessional(uid, job);
        job.setStatus("CANCELLED");
        job.setCounterOfferSender(null);
        return JobDto.from(jobs.save(job));
    }

    @Transactional
    public JobDto startService(String uid, String jobId) {
        Job job = findOr404(jobId);
        requireAssignedProfessional(uid, job);
        if (!"ACCEPTED".equals(job.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Solo se puede iniciar un servicio aceptado");
        }
        job.setStatus("IN_PROGRESS");
        job.setCounterOfferSender(null);
        return JobDto.from(jobs.save(job));
    }

    @Transactional
    public JobDto finishService(String uid, String jobId, int stars, String comment) {
        Job job = findOr404(jobId);
        requireParticipant(uid, job);
        if (job.getProfessionalId() == null || job.getProfessionalId().isBlank()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "La solicitud aún no tiene profesional asignado");
        }
        if (!"IN_PROGRESS".equals(job.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Solo se puede finalizar un servicio en progreso");
        }
        if (stars < 1 || stars > 5) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La calificación es obligatoria (1 a 5 estrellas)");
        }
        job.setStatus("COMPLETED");
        job.setCounterOfferSender(null);
        jobs.save(job);
        // Calificación de quien finaliza (cliente o profesional) en la misma operación.
        try {
            ratingService.rate(uid, jobId,
                new com.galibu.backend.ratings.dto.CreateRatingRequest(stars, comment));
        } catch (ResponseStatusException ex) {
            if (ex.getStatusCode() != HttpStatus.CONFLICT) {
                throw ex;
            }
            // Ya calificado antes (reintento): se completa igual.
        }
        return JobDto.from(findOr404(jobId));
    }

    /**
     * No se permite una solicitud activa con igual categoría y ubicación
     * (departamento + municipio + dirección, insensible a mayúsculas).
     * Otra dirección con igual categoría sí se permite. Las cerradas
     * (COMPLETED/CANCELLED) no bloquean.
     */
    private void rejectDuplicateActiveRequest(String uid, CreateJobRequest req) {
        for (Job j : jobs.findByClientIdOrderByCreatedAtDesc(uid)) {
            if ("COMPLETED".equals(j.getStatus()) || "CANCELLED".equals(j.getStatus())) {
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

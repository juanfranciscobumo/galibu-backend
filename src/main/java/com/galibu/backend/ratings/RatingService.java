package com.galibu.backend.ratings;

import com.galibu.backend.jobs.Job;
import com.galibu.backend.jobs.JobRepository;
import com.galibu.backend.ratings.dto.CreateRatingRequest;
import com.galibu.backend.ratings.dto.RatingDto;
import com.galibu.backend.users.User;
import com.galibu.backend.users.UserRepository;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class RatingService {

    private final RatingRepository ratings;
    private final JobRepository jobs;
    private final UserRepository users;

    public RatingService(RatingRepository ratings, JobRepository jobs, UserRepository users) {
        this.ratings = ratings;
        this.jobs = jobs;
        this.users = users;
    }

    @Transactional
    public RatingDto rate(String uid, String jobId, CreateRatingRequest req) {
        Job job = jobs.findById(jobId).orElseThrow(() ->
            new ResponseStatusException(HttpStatus.NOT_FOUND, "Solicitud no existe"));
        if (!"IN_PROGRESS".equals(job.getStatus()) && !"COMPLETED".equals(job.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Solo se puede calificar un servicio en curso o finalizado");
        }
        // Un usuario BOTH que se asignó a sí mismo no puede calificarse.
        if (job.getProfessionalId() != null && job.getProfessionalId().equals(job.getClientId())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "No puedes calificar tu propia solicitud");
        }
        String ratedId;
        if (uid.equals(job.getClientId())) {
            if (job.getProfessionalId() == null || job.getProfessionalId().isBlank()) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "La solicitud aún no tiene profesional asignado");
            }
            ratedId = job.getProfessionalId();
        } else if (uid.equals(job.getProfessionalId())) {
            ratedId = job.getClientId();
        } else {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "No participas en esta solicitud");
        }
        if (ratings.existsByJobIdAndRaterId(jobId, uid)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Ya calificaste este servicio");
        }
        User rated = users.findById(ratedId).orElseThrow(() ->
            new ResponseStatusException(HttpStatus.NOT_FOUND, "Usuario no existe"));

        Rating rating = new Rating();
        rating.setJobId(jobId);
        rating.setRaterId(uid);
        rating.setRatedId(ratedId);
        rating.setStars(req.getStars());
        rating.setComment(req.getComment());
        rating.setCreatedAt(System.currentTimeMillis());
        Rating saved = ratings.save(rating);

        // Espejo en la solicitud para que el sync lo propague a las apps.
        if (uid.equals(job.getClientId())) {
            job.setClientRatingOfPro((float) req.getStars());
            job.setClientReviewOfPro(req.getComment());
        } else {
            job.setProRatingOfClient((float) req.getStars());
            job.setProReviewOfClient(req.getComment());
        }
        jobs.save(job);

        // Promedio del calificado con todas sus calificaciones recibidas.
        List<Rating> received = ratings.findByRatedIdOrderByCreatedAtDesc(ratedId);
        double avg = received.stream().mapToInt(Rating::getStars).average().orElse(req.getStars());
        rated.setRating((float) avg);
        users.save(rated);

        return RatingDto.from(saved);
    }

    @Transactional(readOnly = true)
    public List<RatingDto> listForJob(String uid, String jobId) {
        Job job = jobs.findById(jobId).orElseThrow(() ->
            new ResponseStatusException(HttpStatus.NOT_FOUND, "Solicitud no existe"));
        if (!uid.equals(job.getClientId()) && !uid.equals(job.getProfessionalId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "No participas en esta solicitud");
        }
        return ratings.findByJobIdOrderByCreatedAtDesc(jobId).stream()
            .map(RatingDto::from)
            .toList();
    }

    @Transactional(readOnly = true)
    public List<RatingDto> listForUser(String userId) {
        users.findById(userId).orElseThrow(() ->
            new ResponseStatusException(HttpStatus.NOT_FOUND, "Usuario no existe"));
        return ratings.findByRatedIdOrderByCreatedAtDesc(userId).stream()
            .map(RatingDto::from)
            .toList();
    }
}

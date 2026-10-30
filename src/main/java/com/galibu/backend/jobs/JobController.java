package com.galibu.backend.jobs;

import com.galibu.backend.jobs.dto.CounterRequest;
import com.galibu.backend.jobs.dto.CreateJobRequest;
import com.galibu.backend.jobs.dto.JobDto;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/jobs")
public class JobController {

    private final JobService service;

    public JobController(JobService service) {
        this.service = service;
    }

    @GetMapping("/open")
    public List<JobDto> open() {
        return service.list(null, null, null, "OPEN");
    }

    @GetMapping
    public List<JobDto> list(
        Authentication auth,
        @RequestParam(required = false) String clientId,
        @RequestParam(required = false) String professionalId,
        @RequestParam(required = false) String status
    ) {
        return service.list(auth.getName(), clientId, professionalId, status);
    }

    @GetMapping("/{id}")
    public JobDto get(Authentication auth, @PathVariable String id) {
        return service.get(auth.getName(), id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public JobDto create(Authentication auth, @Valid @RequestBody CreateJobRequest request) {
        return service.create(auth.getName(), request);
    }

    @PatchMapping("/{id}/cancel")
    public JobDto cancel(
        Authentication auth, @PathVariable String id,
        @RequestParam(required = false) String reason
    ) {
        return service.cancel(auth.getName(), id, reason);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(Authentication auth, @PathVariable String id) {
        service.delete(auth.getName(), id);
    }

    @PostMapping("/{id}/counter")
    public JobDto counter(
        Authentication auth, @PathVariable String id, @Valid @RequestBody CounterRequest request
    ) {
        return service.counter(auth.getName(), id, request);
    }

    @PostMapping("/{id}/accept-direct")
    public JobDto acceptDirect(Authentication auth, @PathVariable String id) {
        return service.acceptDirect(auth.getName(), id);
    }

    @PostMapping("/{id}/reject-direct")
    public JobDto rejectDirect(Authentication auth, @PathVariable String id) {
        return service.rejectDirect(auth.getName(), id);
    }

    @PostMapping("/{id}/start")
    public JobDto startService(Authentication auth, @PathVariable String id) {
        return service.startService(auth.getName(), id);
    }

    @PostMapping("/{id}/finish")
    public JobDto finishService(
        Authentication auth, @PathVariable String id, @Valid @RequestBody FinishServiceRequest request
    ) {
        return service.finishService(auth.getName(), id, request.stars(), request.comment());
    }

    public record FinishServiceRequest(
        @Min(value = 1, message = "La calificación es obligatoria (1 a 5 estrellas)")
        @Max(value = 5, message = "La calificación es obligatoria (1 a 5 estrellas)")
        int stars,
        @Size(max = 2000) String comment
    ) {
    }
}

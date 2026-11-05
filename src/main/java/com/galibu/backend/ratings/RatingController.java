package com.galibu.backend.ratings;

import com.galibu.backend.ratings.dto.CreateRatingRequest;
import com.galibu.backend.ratings.dto.RatingDto;
import jakarta.validation.Valid;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class RatingController {

    private static final Logger log = LoggerFactory.getLogger(RatingController.class);

    private final RatingService service;

    public RatingController(RatingService service) {
        this.service = service;
    }

    @PostMapping("/jobs/{jobId}/ratings")
    @ResponseStatus(HttpStatus.CREATED)
    public RatingDto rate(
        Authentication auth, @PathVariable String jobId, @Valid @RequestBody CreateRatingRequest request
    ) {
        log.info("ratings rate userId={} jobId={} stars={}", auth.getName(), jobId, request.getStars());
        return service.rate(auth.getName(), jobId, request);
    }

    @GetMapping("/jobs/{jobId}/ratings")
    public List<RatingDto> listForJob(Authentication auth, @PathVariable String jobId) {
        log.debug("ratings listForJob userId={} jobId={}", auth.getName(), jobId);
        return service.listForJob(auth.getName(), jobId);
    }

    @GetMapping("/users/{userId}/ratings")
    public List<RatingDto> listForUser(@PathVariable String userId) {
        log.debug("ratings listForUser userId={}", userId);
        return service.listForUser(userId);
    }
}
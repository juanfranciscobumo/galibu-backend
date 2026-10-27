package com.galibu.backend.ratings;

import com.galibu.backend.ratings.dto.CreateRatingRequest;
import com.galibu.backend.ratings.dto.RatingDto;
import jakarta.validation.Valid;
import java.util.List;
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

    private final RatingService service;

    public RatingController(RatingService service) {
        this.service = service;
    }

    @PostMapping("/jobs/{jobId}/ratings")
    @ResponseStatus(HttpStatus.CREATED)
    public RatingDto rate(
        Authentication auth, @PathVariable String jobId, @Valid @RequestBody CreateRatingRequest request
    ) {
        return service.rate(auth.getName(), jobId, request);
    }

    @GetMapping("/jobs/{jobId}/ratings")
    public List<RatingDto> listForJob(Authentication auth, @PathVariable String jobId) {
        return service.listForJob(auth.getName(), jobId);
    }

    @GetMapping("/users/{userId}/ratings")
    public List<RatingDto> listForUser(@PathVariable String userId) {
        return service.listForUser(userId);
    }
}

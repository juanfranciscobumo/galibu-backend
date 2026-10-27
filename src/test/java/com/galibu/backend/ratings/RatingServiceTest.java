package com.galibu.backend.ratings;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.galibu.backend.jobs.Job;
import com.galibu.backend.jobs.JobRepository;
import com.galibu.backend.ratings.dto.CreateRatingRequest;
import com.galibu.backend.ratings.dto.RatingDto;
import com.galibu.backend.users.User;
import com.galibu.backend.users.UserRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class RatingServiceTest {

    @Mock
    private RatingRepository ratings;

    @Mock
    private JobRepository jobs;

    @Mock
    private UserRepository users;

    @InjectMocks
    private RatingService service;

    private Job job(String status) {
        Job j = new Job();
        j.setId("job-1");
        j.setTitle("T");
        j.setStatus(status);
        j.setClientId("client-1");
        j.setClientName("Cli");
        j.setProfessionalId("pro-1");
        j.setBudgetMin(10000.0);
        return j;
    }

    private User user(String id) {
        User u = new User();
        u.setId(id);
        u.setName("U");
        u.setEmail(id + "@t.com");
        u.setRole("PROFESSIONAL");
        u.setRating(5.0f);
        return u;
    }

    @Test
    void rateSavesAndUpdatesAverage() {
        when(jobs.findById("job-1")).thenReturn(Optional.of(job("IN_PROGRESS")));
        when(ratings.existsByJobIdAndRaterId("job-1", "client-1")).thenReturn(false);
        when(users.findById("pro-1")).thenReturn(Optional.of(user("pro-1")));
        when(ratings.save(any(Rating.class))).thenAnswer(i -> i.getArgument(0));
        Rating previous = new Rating();
        previous.setStars(4);
        when(ratings.findByRatedIdOrderByCreatedAtDesc("pro-1")).thenReturn(List.of(previous));
        when(users.save(any(User.class))).thenAnswer(i -> i.getArgument(0));

        RatingDto dto = service.rate("client-1", "job-1", new CreateRatingRequest(5, "Excelente"));

        assertEquals(5, dto.getStars());
        assertEquals("pro-1", dto.getRatedId());
        verify(users).save(any(User.class));
        verify(jobs).save(any(Job.class));
    }

    @Test
    void rateGuardsStateParticipantAndDuplicate() {
        when(jobs.findById("job-1")).thenReturn(Optional.of(job("OPEN")));
        ResponseStatusException badState = assertThrows(ResponseStatusException.class,
            () -> service.rate("client-1", "job-1", new CreateRatingRequest(5, null)));
        assertEquals(HttpStatus.CONFLICT, badState.getStatusCode());

        when(jobs.findById("job-1")).thenReturn(Optional.of(job("COMPLETED")));
        ResponseStatusException forbidden = assertThrows(ResponseStatusException.class,
            () -> service.rate("other", "job-1", new CreateRatingRequest(5, null)));
        assertEquals(HttpStatus.FORBIDDEN, forbidden.getStatusCode());

        when(ratings.existsByJobIdAndRaterId("job-1", "client-1")).thenReturn(true);
        ResponseStatusException duplicate = assertThrows(ResponseStatusException.class,
            () -> service.rate("client-1", "job-1", new CreateRatingRequest(5, null)));
        assertEquals(HttpStatus.CONFLICT, duplicate.getStatusCode());
    }
}

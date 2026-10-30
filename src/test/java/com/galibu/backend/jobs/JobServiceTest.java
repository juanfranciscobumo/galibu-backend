package com.galibu.backend.jobs;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.galibu.backend.bids.Bid;
import com.galibu.backend.bids.BidRepository;
import com.galibu.backend.ratings.RatingService;
import com.galibu.backend.jobs.dto.CreateJobRequest;
import com.galibu.backend.jobs.dto.CounterRequest;
import com.galibu.backend.jobs.dto.JobDto;
import com.galibu.backend.users.User;
import com.galibu.backend.users.UserRepository;
import com.galibu.backend.wallet.WalletService;
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
class JobServiceTest {

    @Mock
    private JobRepository jobs;

    @Mock
    private UserRepository users;

    @Mock
    private BidRepository bids;

    @Mock
    private RatingService ratingService;

    @Mock
    private WalletService wallet;

    @Mock
    private com.galibu.backend.chat.ChatService chat;

    @InjectMocks
    private JobService service;

    private User client(String id) {
        User u = new User();
        u.setId(id);
        u.setName("Cli");
        u.setEmail(id + "@t.com");
        u.setRole("CLIENT");
        return u;
    }

    private Job job(String id) {
        Job j = new Job();
        j.setId(id);
        j.setTitle("T");
        j.setStatus("OPEN");
        j.setClientId("client-1");
        j.setClientName("Cli");
        j.setBudgetMin(50000.0);
        return j;
    }

    @Test
    void createSetsClientFromTokenAndRejectsNonClients() {
        when(users.findById("client-1")).thenReturn(Optional.of(client("client-1")));
        when(jobs.save(any(Job.class))).thenAnswer(i -> i.getArgument(0));
        CreateJobRequest req = new CreateJobRequest();
        req.setTitle("Plomero");
        req.setCategory("Plomería");
        req.setDescription("fuga en la cocina");
        req.setSuggestedPrice(20000.0);

        JobDto created = service.create("client-1", req);

        assertEquals("client-1", created.getClientId());
        assertEquals("OPEN", created.getStatus());
        assertEquals("Cli", created.getClientName());
        assertEquals("Fuga en la cocina", created.getDescription());
        assertEquals(20000.0, created.getSuggestedPrice());

        User pro = client("pro-1");
        pro.setRole("PROFESSIONAL");
        when(users.findById("pro-1")).thenReturn(Optional.of(pro));
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
            () -> service.create("pro-1", req));
        assertEquals(HttpStatus.FORBIDDEN, ex.getStatusCode());
    }

    @Test
    void getAllowsPublicJobsAndRestrictsPrivateOnes() {
        Job open = job("j1");
        when(jobs.findById("j1")).thenReturn(Optional.of(open));
        assertEquals("j1", service.get("stranger", "j1").getId());

        Job neg = job("j2");
        neg.setStatus("NEGOTIATING");
        when(jobs.findById("j2")).thenReturn(Optional.of(neg));
        assertEquals("j2", service.get("stranger", "j2").getId());

        Job assigned = job("j3");
        assigned.setStatus("NEGOTIATING");
        assigned.setProfessionalId("pro-1");
        when(jobs.findById("j3")).thenReturn(Optional.of(assigned));
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
            () -> service.get("stranger", "j3"));
        assertEquals(HttpStatus.FORBIDDEN, ex.getStatusCode());
    }

    @Test
    void cancelGuardsParticipantAndClosedStates() {
        Job j = job("j1");
        when(jobs.findById("j1")).thenReturn(Optional.of(j));
        when(jobs.save(any(Job.class))).thenAnswer(i -> i.getArgument(0));
        when(users.findById("client-1")).thenReturn(Optional.of(client("client-1")));

        assertEquals("CANCELLED", service.cancel("client-1", "j1", null).getStatus());

        ResponseStatusException forbidden = assertThrows(ResponseStatusException.class,
            () -> service.cancel("other", "j1", null));
        assertEquals(HttpStatus.FORBIDDEN, forbidden.getStatusCode());

        j.setStatus("COMPLETED");
        ResponseStatusException conflict = assertThrows(ResponseStatusException.class,
            () -> service.cancel("client-1", "j1", null));
        assertEquals(HttpStatus.CONFLICT, conflict.getStatusCode());
    }

    @Test
    void startServicePostsSystemChatMessage() {
        Job j = job("j1");
        j.setStatus("ACCEPTED");
        j.setProfessionalId("pro-1");
        when(jobs.findById("j1")).thenReturn(Optional.of(j));
        when(jobs.save(any(Job.class))).thenAnswer(i -> i.getArgument(0));

        service.startService("pro-1", "j1");

        verify(chat).postSystemMessage(eq("j1"), eq("Sistema"),
            org.mockito.ArgumentMatchers.contains("inició el servicio"));
    }

    @Test
    void cancelPostsReasonInSystemChatMessage() {
        Job j = job("j1");
        j.setStatus("ACCEPTED");
        j.setProfessionalId("pro-1");
        when(jobs.findById("j1")).thenReturn(Optional.of(j));
        when(jobs.save(any(Job.class))).thenAnswer(i -> i.getArgument(0));
        when(users.findById("client-1")).thenReturn(Optional.of(client("client-1")));

        service.cancel("client-1", "j1", "no era lo acordado");

        verify(chat).postSystemMessage(eq("j1"), eq("Sistema"),
            org.mockito.ArgumentMatchers.contains("no era lo acordado"));
    }

    @Test
    void counterSetsSenderByRole() {
        Job j = job("j1");
        when(jobs.findById("j1")).thenReturn(Optional.of(j));
        when(jobs.save(any(Job.class))).thenAnswer(i -> i.getArgument(0));

        JobDto byClient = service.counter("client-1", "j1", new CounterRequest(40000.0));
        assertEquals("NEGOTIATING", byClient.getStatus());
        assertEquals("CLIENT", byClient.getCounterOfferSender());
        assertEquals(40000.0, byClient.getCounterOfferAmount());

        Job j2 = job("j2");
        j2.setProfessionalId("pro-1");
        when(jobs.findById("j2")).thenReturn(Optional.of(j2));
        JobDto byPro = service.counter("pro-1", "j2", new CounterRequest(45000.0));
        assertEquals("PROFESSIONAL", byPro.getCounterOfferSender());
    }

    @Test
    void acceptDirectAssignsPriceWithPrecedenceAndGuards() {
        Job j = job("j1");
        j.setStatus("NEGOTIATING");
        j.setProfessionalId("pro-1");
        j.setCounterOfferAmount(42000.0);
        j.setProHourlyRate(50000.0);
        when(jobs.findById("j1")).thenReturn(Optional.of(j));
        when(jobs.save(any(Job.class))).thenAnswer(i -> i.getArgument(0));

        JobDto accepted = service.acceptDirect("pro-1", "j1");
        assertEquals("ACCEPTED", accepted.getStatus());
        assertEquals(42000.0, accepted.getFinalPrice());

        ResponseStatusException forbidden = assertThrows(ResponseStatusException.class,
            () -> service.acceptDirect("other", "j1"));
        assertEquals(HttpStatus.FORBIDDEN, forbidden.getStatusCode());

        j.setStatus("ACCEPTED");
        ResponseStatusException conflict = assertThrows(ResponseStatusException.class,
            () -> service.acceptDirect("pro-1", "j1"));
        assertEquals(HttpStatus.CONFLICT, conflict.getStatusCode());
    }

    @Test
    void acceptDirectAllowsClientOnlyOnProCounter() {
        Job j = job("j1");
        j.setStatus("NEGOTIATING");
        j.setProfessionalId("pro-1");
        j.setCounterOfferAmount(38000.0);
        j.setCounterOfferSender("PROFESSIONAL");
        when(jobs.findById("j1")).thenReturn(Optional.of(j));
        when(jobs.save(any(Job.class))).thenAnswer(i -> i.getArgument(0));

        JobDto accepted = service.acceptDirect("client-1", "j1");
        assertEquals("ACCEPTED", accepted.getStatus());
        assertEquals(38000.0, accepted.getFinalPrice());

        Job j2 = job("j2");
        j2.setStatus("NEGOTIATING");
        j2.setProfessionalId("pro-1");
        when(jobs.findById("j2")).thenReturn(Optional.of(j2));

        ResponseStatusException forbidden = assertThrows(ResponseStatusException.class,
            () -> service.acceptDirect("client-1", "j2"));
        assertEquals(HttpStatus.FORBIDDEN, forbidden.getStatusCode());
    }

    @Test
    void createWithProfessionalAssignsAndNegotiates() {        when(users.findById("client-1")).thenReturn(Optional.of(client("client-1")));
        User pro = client("pro-1");
        pro.setRole("PROFESSIONAL");
        when(users.findById("pro-1")).thenReturn(Optional.of(pro));
        when(jobs.save(any(Job.class))).thenAnswer(i -> i.getArgument(0));
        CreateJobRequest req = new CreateJobRequest();
        req.setTitle("Directa");
        req.setProfessionalId("pro-1");

        JobDto created = service.create("client-1", req);

        assertEquals("NEGOTIATING", created.getStatus());
        assertEquals("pro-1", created.getProfessionalId());

        User notPro = client("other");
        when(users.findById("other")).thenReturn(Optional.of(notPro));
        CreateJobRequest bad = new CreateJobRequest();
        bad.setTitle("X");
        bad.setProfessionalId("other");
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
            () -> service.create("client-1", bad));
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
    }

    @Test
    void createHonorsClientProvidedIdAndRejectsDuplicates() {
        when(users.findById("client-1")).thenReturn(Optional.of(client("client-1")));
        when(jobs.existsById("chat_pro-1_123")).thenReturn(false);
        when(jobs.save(any(Job.class))).thenAnswer(i -> i.getArgument(0));
        CreateJobRequest req = new CreateJobRequest();
        req.setTitle("Chat directo");
        req.setId("chat_pro-1_123");

        JobDto created = service.create("client-1", req);

        assertEquals("chat_pro-1_123", created.getId());
        assertEquals("OPEN", created.getStatus());

        when(jobs.existsById("chat_pro-1_123")).thenReturn(true);
        ResponseStatusException dup = assertThrows(ResponseStatusException.class,
            () -> service.create("client-1", req));
        assertEquals(HttpStatus.CONFLICT, dup.getStatusCode());
    }

    @Test
    void createRejectsDuplicateActiveLocationCategory() {
        when(users.findById("client-1")).thenReturn(Optional.of(client("client-1")));
        Job existing = job("j0");
        existing.setStatus("OPEN");
        existing.setCategory("Plomería");
        existing.setDepartment("Antioquia");
        existing.setMunicipality("Rionegro");
        existing.setAddress("Cl 1");
        when(jobs.findByClientIdOrderByCreatedAtDesc("client-1")).thenReturn(List.of(existing));

        CreateJobRequest req = new CreateJobRequest();
        req.setTitle("Plomero");
        req.setCategory("Plomería");
        req.setDepartment("antioquia");
        req.setMunicipality("RIONEGRO");
        req.setAddress("  cl 1 ");
        ResponseStatusException dup = assertThrows(ResponseStatusException.class,
            () -> service.create("client-1", req));
        assertEquals(HttpStatus.CONFLICT, dup.getStatusCode());

        // Otra dirección sí se permite.
        req.setAddress("Cl 2");
        when(jobs.save(any(Job.class))).thenAnswer(i -> i.getArgument(0));
        JobDto created = service.create("client-1", req);
        assertEquals("OPEN", created.getStatus());

        // Cerradas no bloquean.
        existing.setStatus("CANCELLED");
        req.setAddress("Cl 1");
        JobDto created2 = service.create("client-1", req);
        assertEquals("OPEN", created2.getStatus());
    }

    @Test
    void listEnforcesFilters() {        when(jobs.findByClientIdOrProfessionalIdOrderByCreatedAtDesc("u", "u"))
            .thenReturn(List.of(job("j1")));

        assertEquals(1, service.list("u", null, null, null).size());

        ResponseStatusException e1 = assertThrows(ResponseStatusException.class,
            () -> service.list("u", "other", null, null));
        assertEquals(HttpStatus.FORBIDDEN, e1.getStatusCode());

        ResponseStatusException e2 = assertThrows(ResponseStatusException.class,
            () -> service.list("u", null, null, "NEGOTIATING"));
        assertEquals(HttpStatus.FORBIDDEN, e2.getStatusCode());

        verify(jobs).findByClientIdOrProfessionalIdOrderByCreatedAtDesc("u", "u");
    }

    @Test
    void startAndFinishServiceGuardStates() {
        Job j = job("j1");
        j.setStatus("ACCEPTED");
        j.setProfessionalId("pro-1");
        when(jobs.findById("j1")).thenReturn(Optional.of(j));
        when(jobs.save(any(Job.class))).thenAnswer(i -> i.getArgument(0));

        JobDto started = service.startService("pro-1", "j1");
        assertEquals("IN_PROGRESS", started.getStatus());

        ResponseStatusException notAccepted = assertThrows(ResponseStatusException.class,
            () -> service.startService("pro-1", "j1"));
        assertEquals(HttpStatus.CONFLICT, notAccepted.getStatusCode());

        ResponseStatusException forbidden = assertThrows(ResponseStatusException.class,
            () -> service.finishService("other", "j1", 5, null));
        assertEquals(HttpStatus.FORBIDDEN, forbidden.getStatusCode());

        JobDto finished = service.finishService("pro-1", "j1", 5, "Buen cliente");
        assertEquals("COMPLETED", finished.getStatus());
        verify(ratingService).rate(eq("pro-1"), eq("j1"), any(com.galibu.backend.ratings.dto.CreateRatingRequest.class));

        ResponseStatusException notInProgress = assertThrows(ResponseStatusException.class,
            () -> service.finishService("pro-1", "j1", 5, null));
        assertEquals(HttpStatus.CONFLICT, notInProgress.getStatusCode());
    }

    @Test
    void finishRequiresRating() {
        // El guard de estrellas (1-5) salta antes del save: ese stub sobraría.
        Job j = job("j1");
        j.setStatus("IN_PROGRESS");
        j.setProfessionalId("pro-1");
        when(jobs.findById("j1")).thenReturn(Optional.of(j));

        ResponseStatusException badRating = assertThrows(ResponseStatusException.class,
            () -> service.finishService("pro-1", "j1", 0, null));
        assertEquals(HttpStatus.BAD_REQUEST, badRating.getStatusCode());
    }

    @Test
    void listIncludesNegotiatingJobsWithMyBid() {
        Job negotiating = job("j9");
        negotiating.setStatus("NEGOTIATING");
        negotiating.setClientId("client-9");
        negotiating.setProfessionalId(null);
        Bid myBid = new Bid();
        myBid.setId("bid_j9_pro-1");
        myBid.setJobId("j9");
        myBid.setProfessionalId("pro-1");
        when(jobs.findByProfessionalIdOrderByCreatedAtDesc("pro-1")).thenReturn(List.of());
        when(bids.findByProfessionalId("pro-1")).thenReturn(List.of(myBid));
        when(jobs.findById("j9")).thenReturn(Optional.of(negotiating));

        List<JobDto> result = service.list("pro-1", null, "pro-1", null);

        assertEquals(1, result.size());
        assertEquals("j9", result.get(0).getId());
    }
}

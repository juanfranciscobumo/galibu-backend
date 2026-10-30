package com.galibu.backend.bids;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.galibu.backend.bids.dto.BidDto;
import com.galibu.backend.bids.dto.CreateBidRequest;
import com.galibu.backend.jobs.Job;
import com.galibu.backend.jobs.JobRepository;
import com.galibu.backend.jobs.dto.CounterRequest;
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
class BidServiceTest {

    @Mock
    private BidRepository bids;

    @Mock
    private JobRepository jobs;

    @Mock
    private UserRepository users;

    @Mock
    private WalletService wallet;

    @Mock
    private com.galibu.backend.chat.ChatService chat;

    @InjectMocks
    private BidService service;

    private User pro(String id) {
        User u = new User();
        u.setId(id);
        u.setName("Pro");
        u.setEmail(id + "@t.com");
        u.setRole("PROFESSIONAL");
        u.setRating(4.8f);
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

    private com.galibu.backend.bids.Bid bid(String id, String status) {
        com.galibu.backend.bids.Bid b = new com.galibu.backend.bids.Bid();
        b.setId(id);
        b.setJobId("job-1");
        b.setProfessionalId("pro-1");
        b.setProfessionalName("Pro");
        b.setAmount(10000.0);
        b.setComment("C");
        b.setStatus(status);
        return b;
    }

    @Test
    void submitCreatesSinglePendingBidAndFlipsOpenJob() {
        when(users.findById("pro-1")).thenReturn(Optional.of(pro("pro-1")));
        when(jobs.findById("job-1")).thenReturn(Optional.of(job("job-1")));
        when(bids.findAllByJobIdAndProfessionalId("job-1", "pro-1")).thenReturn(List.of());
        when(bids.save(any(com.galibu.backend.bids.Bid.class))).thenAnswer(i -> i.getArgument(0));
        when(jobs.save(any(Job.class))).thenAnswer(i -> i.getArgument(0));

        CreateBidRequest req = new CreateBidRequest();
        req.setAmount(10000.0);
        req.setComment("C");
        req.setDurationHours(2);
        BidDto created = service.submit("pro-1", "job-1", req);

        assertEquals("bid_job-1_pro-1", created.getId());
        assertEquals("PENDING", created.getStatus());
        verify(bids, never()).delete(any());
    }

    @Test
    void submitSnapshotsSuggestedPriceFromJob() {
        when(users.findById("pro-1")).thenReturn(Optional.of(pro("pro-1")));
        Job j = job("job-1");
        j.setSuggestedPrice(8000.0);
        when(jobs.findById("job-1")).thenReturn(Optional.of(j));
        when(bids.findAllByJobIdAndProfessionalId("job-1", "pro-1")).thenReturn(List.of());
        when(bids.save(any(com.galibu.backend.bids.Bid.class))).thenAnswer(i -> i.getArgument(0));

        CreateBidRequest req = new CreateBidRequest();
        req.setAmount(10000.0);
        BidDto created = service.submit("pro-1", "job-1", req);

        assertEquals(8000.0, created.getSuggestedPrice());
    }

    @Test
    void submitTwiceUpdatesInsteadOfDuplicatingAndCleansLegacy() {
        when(users.findById("pro-1")).thenReturn(Optional.of(pro("pro-1")));
        Job j = job("job-1");
        j.setStatus("NEGOTIATING");
        when(jobs.findById("job-1")).thenReturn(Optional.of(j));
        com.galibu.backend.bids.Bid legacy = bid("bid_old", "PENDING");
        when(bids.findAllByJobIdAndProfessionalId("job-1", "pro-1")).thenReturn(List.of(legacy));
        when(bids.save(any(com.galibu.backend.bids.Bid.class))).thenAnswer(i -> i.getArgument(0));

        CreateBidRequest req = new CreateBidRequest();
        req.setAmount(12000.0);
        BidDto updated = service.submit("pro-1", "job-1", req);

        assertEquals("bid_job-1_pro-1", updated.getId());
        assertEquals("COUNTERED_BY_PROFESSIONAL", updated.getStatus());
        assertEquals(12000.0, updated.getLastCounterOfferAmount());
        verify(bids).delete(legacy);
    }

    @Test
    void submitRejectsClientsAndClosedJobs() {        User client = pro("client-1");
        client.setRole("CLIENT");
        when(users.findById("client-1")).thenReturn(Optional.of(client));
        CreateBidRequest req = new CreateBidRequest();
        req.setAmount(1000.0);
        ResponseStatusException forbidden = assertThrows(ResponseStatusException.class,
            () -> service.submit("client-1", "job-1", req));
        assertEquals(HttpStatus.FORBIDDEN, forbidden.getStatusCode());

        when(users.findById("pro-1")).thenReturn(Optional.of(pro("pro-1")));
        Job closed = job("job-1");
        closed.setStatus("ACCEPTED");
        when(jobs.findById("job-1")).thenReturn(Optional.of(closed));
        ResponseStatusException conflict = assertThrows(ResponseStatusException.class,
            () -> service.submit("pro-1", "job-1", req));
        assertEquals(HttpStatus.CONFLICT, conflict.getStatusCode());
    }

    @Test
    void submitRejectsOwnJobForBothRole() {
        User both = pro("both-1");
        both.setRole("BOTH");
        Job own = job("job-1");
        own.setClientId("both-1");
        when(users.findById("both-1")).thenReturn(Optional.of(both));
        when(jobs.findById("job-1")).thenReturn(Optional.of(own));

        CreateBidRequest req = new CreateBidRequest();
        req.setAmount(1000.0);
        ResponseStatusException conflict = assertThrows(ResponseStatusException.class,
            () -> service.submit("both-1", "job-1", req));
        assertEquals(HttpStatus.CONFLICT, conflict.getStatusCode());
        verify(bids, never()).save(any(com.galibu.backend.bids.Bid.class));
    }

    @Test
    void counterSetsStatusByRole() {
        when(bids.findById("bid-1")).thenReturn(Optional.of(bid("bid-1", "PENDING")));
        when(jobs.findById("job-1")).thenReturn(Optional.of(job("job-1")));
        when(bids.save(any(com.galibu.backend.bids.Bid.class))).thenAnswer(i -> i.getArgument(0));

        BidDto byClient = service.counter("client-1", "bid-1", new CounterRequest(9000.0));
        assertEquals("COUNTERED_BY_CLIENT", byClient.getStatus());
        assertEquals(9000.0, byClient.getAmount());

        ResponseStatusException forbidden = assertThrows(ResponseStatusException.class,
            () -> service.counter("stranger", "bid-1", new CounterRequest(9000.0)));
        assertEquals(HttpStatus.FORBIDDEN, forbidden.getStatusCode());
    }

    @Test
    void acceptAssignsJobAndRejectsOthers() {
        com.galibu.backend.bids.Bid mine = bid("bid-1", "PENDING");
        com.galibu.backend.bids.Bid other = bid("bid-2", "PENDING");
        other.setProfessionalId("pro-2");
        when(bids.findById("bid-1")).thenReturn(Optional.of(mine));
        when(jobs.findById("job-1")).thenReturn(Optional.of(job("job-1")));
        when(bids.findByJobIdOrderByCreatedAtDesc("job-1")).thenReturn(List.of(mine, other));
        when(bids.save(any(com.galibu.backend.bids.Bid.class))).thenAnswer(i -> i.getArgument(0));
        when(jobs.save(any(Job.class))).thenAnswer(i -> i.getArgument(0));

        BidDto accepted = service.accept("client-1", "bid-1");

        assertEquals("ACCEPTED", accepted.getStatus());
        assertEquals("REJECTED", other.getStatus());
        verify(jobs).save(any(Job.class));

        ResponseStatusException forbidden = assertThrows(ResponseStatusException.class,
            () -> service.accept("pro-1", "bid-1"));
        assertEquals(HttpStatus.FORBIDDEN, forbidden.getStatusCode());
    }

    @Test
    void listForJobShowsAllToClientAndOwnToOthers() {        com.galibu.backend.bids.Bid mine = bid("bid-1", "PENDING");
        com.galibu.backend.bids.Bid other = bid("bid-2", "PENDING");
        other.setProfessionalId("pro-2");
        when(jobs.findById("job-1")).thenReturn(Optional.of(job("job-1")));
        when(bids.findByJobIdOrderByCreatedAtDesc("job-1")).thenReturn(List.of(mine, other));

        assertEquals(2, service.listForJob("client-1", "job-1").size());
        assertEquals(1, service.listForJob("pro-1", "job-1").size());
    }

    @Test
    void mineReturnsOwnBids() {
        when(bids.findByProfessionalId("pro-1")).thenReturn(List.of(bid("bid-1", "PENDING")));

        assertEquals(1, service.mine("pro-1").size());
    }

    @Test
    void acceptAllowsCounteredOwnerProfessional() {
        com.galibu.backend.bids.Bid countered = bid("bid-1", "COUNTERED_BY_CLIENT");
        countered.setProfessionalId("pro-1");
        Job j = job("job-1");
        j.setStatus("NEGOTIATING");
        when(bids.findById("bid-1")).thenReturn(Optional.of(countered));
        when(jobs.findById("job-1")).thenReturn(Optional.of(j));
        when(bids.findByJobIdOrderByCreatedAtDesc("job-1")).thenReturn(List.of(countered));
        when(bids.save(any(com.galibu.backend.bids.Bid.class))).thenAnswer(i -> i.getArgument(0));
        when(jobs.save(any(Job.class))).thenAnswer(i -> i.getArgument(0));

        BidDto accepted = service.accept("pro-1", "bid-1");

        assertEquals("ACCEPTED", accepted.getStatus());
        assertEquals("ACCEPTED", j.getStatus());
        assertEquals("pro-1", j.getProfessionalId());

        ResponseStatusException stranger = assertThrows(ResponseStatusException.class,
            () -> service.accept("stranger", "bid-1"));
        assertEquals(HttpStatus.FORBIDDEN, stranger.getStatusCode());
    }

    @Test
    void submitRejectsWhileBidAwaitingClient() {
        when(users.findById("pro-1")).thenReturn(Optional.of(pro("pro-1")));
        Job j = job("job-1");
        j.setStatus("NEGOTIATING");
        when(jobs.findById("job-1")).thenReturn(Optional.of(j));
        com.galibu.backend.bids.Bid awaiting = bid("bid_job-1_pro-1", "PENDING");
        when(bids.findAllByJobIdAndProfessionalId("job-1", "pro-1")).thenReturn(List.of(awaiting));

        CreateBidRequest req = new CreateBidRequest();
        req.setAmount(15000.0);
        ResponseStatusException conflict = assertThrows(ResponseStatusException.class,
            () -> service.submit("pro-1", "job-1", req));
        assertEquals(HttpStatus.CONFLICT, conflict.getStatusCode());
    }
}

package com.galibu.backend.bids;

import com.galibu.backend.bids.dto.BidDto;
import com.galibu.backend.bids.dto.CreateBidRequest;
import com.galibu.backend.jobs.dto.CounterRequest;
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
public class BidController {

    private static final Logger log = LoggerFactory.getLogger(BidController.class);

    private final BidService service;

    public BidController(BidService service) {
        this.service = service;
    }

    @GetMapping("/jobs/{jobId}/bids")
    public List<BidDto> listForJob(Authentication auth, @PathVariable String jobId) {
        log.debug("bids listForJob userId={} jobId={}", auth.getName(), jobId);
        return service.listForJob(auth.getName(), jobId);
    }

    @GetMapping("/bids/mine")
    public List<BidDto> mine(Authentication auth) {
        log.debug("bids mine userId={}", auth.getName());
        return service.mine(auth.getName());
    }

    @PostMapping("/jobs/{jobId}/bids")
    @ResponseStatus(HttpStatus.CREATED)
    public BidDto submit(
        Authentication auth, @PathVariable String jobId, @Valid @RequestBody CreateBidRequest request
    ) {
        log.info("bids submit userId={} jobId={}", auth.getName(), jobId);
        return service.submit(auth.getName(), jobId, request);
    }

    @PostMapping("/bids/{bidId}/counter")
    public BidDto counter(
        Authentication auth, @PathVariable String bidId, @Valid @RequestBody CounterRequest request
    ) {
        log.info("bids counter userId={} bidId={}", auth.getName(), bidId);
        return service.counter(auth.getName(), bidId, request);
    }

    @PostMapping("/bids/{bidId}/reject")
    public BidDto reject(Authentication auth, @PathVariable String bidId) {
        log.info("bids reject userId={} bidId={}", auth.getName(), bidId);
        return service.reject(auth.getName(), bidId);
    }

    @PostMapping("/bids/{bidId}/accept")
    public BidDto accept(Authentication auth, @PathVariable String bidId) {
        log.info("bids accept userId={} bidId={}", auth.getName(), bidId);
        return service.accept(auth.getName(), bidId);
    }
}
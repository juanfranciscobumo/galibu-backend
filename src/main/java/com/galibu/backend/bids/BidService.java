package com.galibu.backend.bids;

import com.galibu.backend.bids.dto.BidDto;
import com.galibu.backend.bids.dto.CreateBidRequest;
import com.galibu.backend.jobs.Job;
import com.galibu.backend.jobs.JobRepository;
import com.galibu.backend.jobs.dto.CounterRequest;
import com.galibu.backend.users.User;
import com.galibu.backend.users.UserRepository;
import com.galibu.backend.wallet.WalletService;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class BidService {

    private final BidRepository bids;
    private final JobRepository jobs;
    private final UserRepository users;
    private final WalletService wallet;

    public BidService(BidRepository bids, JobRepository jobs, UserRepository users, WalletService wallet) {
        this.bids = bids;
        this.jobs = jobs;
        this.users = users;
        this.wallet = wallet;
    }

    @Transactional(readOnly = true)
    public List<BidDto> listForJob(String uid, String jobId) {
        Job job = findJobOr404(jobId);
        List<Bid> all = bids.findByJobIdOrderByCreatedAtDesc(jobId);
        if (uid.equals(job.getClientId())) {
            return toDto(all);
        }
        return toDto(all.stream().filter(b -> uid.equals(b.getProfessionalId())).toList());
    }

    @Transactional(readOnly = true)
    public List<BidDto> mine(String uid) {
        return toDto(bids.findByProfessionalId(uid));
    }

    @Transactional
    public BidDto submit(String uid, String jobId, CreateBidRequest req) {
        User user = findUserOr404(uid);
        if (!"PROFESSIONAL".equals(user.getRole()) && !"BOTH".equals(user.getRole())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Solo un profesional puede ofertar");
        }
        Job job = findJobOr404(jobId);
        if (!"OPEN".equals(job.getStatus()) && !"NEGOTIATING".equals(job.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "La solicitud no admite ofertas en su estado actual");
        }
        String bidId = "bid_" + jobId + "_" + uid;
        List<Bid> previous = bids.findAllByJobIdAndProfessionalId(jobId, uid);
        for (Bid dupe : previous) {
            if (!dupe.getId().equals(bidId)) {
                bids.delete(dupe);
            }
        }
        Bid existing = previous.stream().filter(b -> b.getId().equals(bidId)).findFirst().orElse(null);
        if (existing != null && ("PENDING".equals(existing.getStatus())
                || "COUNTERED_BY_PROFESSIONAL".equals(existing.getStatus()))) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                "Ya tienes una oferta en espera de respuesta del cliente");
        }
        boolean isCounter = existing != null || !previous.isEmpty();
        Bid bid = existing != null ? existing : new Bid();
        bid.setId(bidId);
        bid.setJobId(jobId);
        bid.setProfessionalId(uid);
        bid.setProfessionalName(user.getName());
        bid.setProfessionalRating(user.getRating());
        bid.setProfessionalPhoto(user.getProfilePhotoUrl());
        bid.setAmount(req.getAmount());
        bid.setSuggestedPrice(job.getSuggestedPrice());
        if (req.getComment() != null && !req.getComment().isBlank()) {
            bid.setComment(req.getComment());
        } else if (bid.getComment() == null) {
            bid.setComment("");
        }
        bid.setDurationHours(req.getDurationHours() > 0 ? req.getDurationHours()
            : (existing != null ? existing.getDurationHours() : 1));
        bid.setStatus(isCounter ? "COUNTERED_BY_PROFESSIONAL" : "PENDING");
        Double lastCounter = null;
        if (isCounter) {
            lastCounter = req.getAmount();
        } else if (existing != null) {
            lastCounter = existing.getLastCounterOfferAmount();
        }
        bid.setLastCounterOfferAmount(lastCounter);
        Bid saved = bids.save(bid);
        if ("OPEN".equals(job.getStatus())) {
            job.setStatus("NEGOTIATING");
            job.setCounterOfferAmount(req.getAmount());
            job.setCounterOfferSender("PROFESSIONAL");
            jobs.save(job);
        }
        return BidDto.from(saved);
    }

    @Transactional
    public BidDto counter(String uid, String bidId, CounterRequest req) {
        Bid bid = findBidOr404(bidId);
        Job job = findJobOr404(bid.getJobId());
        boolean isClient = uid.equals(job.getClientId());
        boolean isOwner = uid.equals(bid.getProfessionalId());
        if (!isClient && !isOwner) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "No participas en esta oferta");
        }
        if (!"OPEN".equals(job.getStatus()) && !"NEGOTIATING".equals(job.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "La solicitud no admite contraofertas en su estado actual");
        }
        bid.setAmount(req.getAmount());
        bid.setLastCounterOfferAmount(req.getAmount());
        bid.setStatus(isClient ? "COUNTERED_BY_CLIENT" : "COUNTERED_BY_PROFESSIONAL");
        return BidDto.from(bids.save(bid));
    }

    @Transactional
    public BidDto reject(String uid, String bidId) {
        Bid bid = findBidOr404(bidId);
        Job job = findJobOr404(bid.getJobId());
        if (!uid.equals(job.getClientId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Solo el cliente puede rechazar ofertas");
        }
        bid.setStatus("REJECTED");
        return BidDto.from(bids.save(bid));
    }

    @Transactional
    public BidDto accept(String uid, String bidId) {
        Bid bid = findBidOr404(bidId);
        Job job = findJobOr404(bid.getJobId());
        boolean isClient = uid.equals(job.getClientId());
        boolean isCounteredOwner = uid.equals(bid.getProfessionalId())
            && "COUNTERED_BY_CLIENT".equals(bid.getStatus());
        if (!isClient && !isCounteredOwner) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Solo el cliente o el profesional contraofertado pueden aceptar");
        }
        if ("ACCEPTED".equals(job.getStatus()) || "COMPLETED".equals(job.getStatus())
            || "CANCELLED".equals(job.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "La solicitud ya está cerrada");
        }
        if ("ACCEPTED".equals(bid.getStatus()) || "REJECTED".equals(bid.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "La oferta ya fue decidida");
        }
        // La tarifa de aceptación la paga el profesional cuya oferta se acepta.
        wallet.chargeAcceptFee(bid.getProfessionalId(), job.getId(), job.getTitle());
        bid.setStatus("ACCEPTED");
        Bid saved = bids.save(bid);
        for (Bid other : bids.findByJobIdOrderByCreatedAtDesc(job.getId())) {
            if (!other.getId().equals(bid.getId()) && !"REJECTED".equals(other.getStatus())) {
                other.setStatus("REJECTED");
                bids.save(other);
            }
        }
        job.setStatus("ACCEPTED");
        job.setProfessionalId(bid.getProfessionalId());
        job.setProfessionalName(bid.getProfessionalName());
        job.setFinalPrice(bid.getAmount());
        jobs.save(job);
        return BidDto.from(saved);
    }

    private Bid findBidOr404(String bidId) {
        return bids.findById(bidId).orElseThrow(() ->
            new ResponseStatusException(HttpStatus.NOT_FOUND, "Oferta no existe"));
    }

    private Job findJobOr404(String jobId) {
        return jobs.findById(jobId).orElseThrow(() ->
            new ResponseStatusException(HttpStatus.NOT_FOUND, "Solicitud no existe"));
    }

    private User findUserOr404(String uid) {
        return users.findById(uid).orElseThrow(() ->
            new ResponseStatusException(HttpStatus.NOT_FOUND, "Usuario no existe"));
    }

    private List<BidDto> toDto(List<Bid> list) {
        return list.stream().map(BidDto::from).toList();
    }
}

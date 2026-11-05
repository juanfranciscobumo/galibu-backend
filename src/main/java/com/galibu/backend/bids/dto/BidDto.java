package com.galibu.backend.bids.dto;

import com.galibu.backend.bids.Bid;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class BidDto {

    public String id;
    public String jobId;
    public String professionalId;
    public String professionalName;
    public float professionalRating;
    public String professionalPhoto;
    public double amount;
    public String comment;
    public int durationHours;
    public String status;
    public Double lastCounterOfferAmount;
    public Double suggestedPrice;
    public long createdAt;

    public static BidDto from(Bid bid) {
        BidDto dto = new BidDto();
        dto.setId(bid.getId());
        dto.setJobId(bid.getJobId());
        dto.setProfessionalId(bid.getProfessionalId());
        dto.setProfessionalName(bid.getProfessionalName());
        dto.setProfessionalRating(bid.getProfessionalRating());
        dto.setProfessionalPhoto(bid.getProfessionalPhoto() == null ? "" : bid.getProfessionalPhoto());
        dto.setAmount(bid.getAmount());
        dto.setComment(bid.getComment() == null ? "" : bid.getComment());
        dto.setDurationHours(bid.getDurationHours());
        dto.setStatus(bid.getStatus());
        dto.setLastCounterOfferAmount(bid.getLastCounterOfferAmount());
        dto.setSuggestedPrice(bid.getSuggestedPrice());
        dto.setCreatedAt(bid.getCreatedAt());
        return dto;
    }
}


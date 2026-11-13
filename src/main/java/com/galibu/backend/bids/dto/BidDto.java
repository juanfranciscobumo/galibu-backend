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
    public String quoteType;
    public boolean visitRequired;
    public Double visitFee;
    public boolean materialsIncluded;
    public Double materialsCost;
    public Long visitScheduledAt;
    public int counterRound;
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
        dto.setQuoteType(bid.getQuoteType() == null ? "ESTIMATED" : bid.getQuoteType());
        dto.setVisitRequired(bid.isVisitRequired());
        dto.setVisitFee(bid.getVisitFee());
        dto.setMaterialsIncluded(bid.isMaterialsIncluded());
        dto.setMaterialsCost(bid.getMaterialsCost());
        dto.setVisitScheduledAt(bid.getVisitScheduledAt());
        dto.setCounterRound(bid.getCounterRound());
        dto.setCreatedAt(bid.getCreatedAt());
        return dto;
    }
}

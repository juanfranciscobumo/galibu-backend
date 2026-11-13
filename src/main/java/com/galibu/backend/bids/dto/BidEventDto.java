package com.galibu.backend.bids.dto;

import com.galibu.backend.bids.BidEvent;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class BidEventDto {

    public String id;
    public String bidId;
    public String jobId;
    public String authorId;
    public String authorRole;
    public String action;
    public double amount;
    public int round;
    public String note;
    public long createdAt;

    public static BidEventDto from(BidEvent event) {
        BidEventDto dto = new BidEventDto();
        dto.setId(event.getId());
        dto.setBidId(event.getBidId());
        dto.setJobId(event.getJobId());
        dto.setAuthorId(event.getAuthorId());
        dto.setAuthorRole(event.getAuthorRole());
        dto.setAction(event.getAction());
        dto.setAmount(event.getAmount());
        dto.setRound(event.getRound());
        dto.setNote(event.getNote() == null ? "" : event.getNote());
        dto.setCreatedAt(event.getCreatedAt());
        return dto;
    }
}

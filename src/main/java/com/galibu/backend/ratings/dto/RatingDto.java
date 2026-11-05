package com.galibu.backend.ratings.dto;

import com.galibu.backend.ratings.Rating;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class RatingDto {

    public Long id;
    public String jobId;
    public String raterId;
    public String ratedId;
    public int stars;
    public String comment;
    public long createdAt;

    public static RatingDto from(Rating r) {
        return new RatingDto(
            r.getId(),
            r.getJobId(),
            r.getRaterId(),
            r.getRatedId(),
            r.getStars(),
            r.getComment(),
            r.getCreatedAt()
        );
    }
}


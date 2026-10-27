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

    private Long id;
    private String jobId;
    private String raterId;
    private String ratedId;
    private int stars;
    private String comment;
    private long createdAt;

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

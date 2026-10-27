package com.galibu.backend.jobs.dto;

import com.galibu.backend.jobs.Job;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class JobDto {

    private String id;
    private String category;
    private String title;
    private String description;
    private double budgetMin;
    private String address;
    private double latitude;
    private double longitude;
    private String status;
    private String clientId;
    private String clientName;
    private String professionalId;
    private String professionalName;
    private Double finalPrice;
    private long createdAt;
    private Float completionRating;
    private String completionReview;
    private Float clientRatingOfPro;
    private String clientReviewOfPro;
    private Float proRatingOfClient;
    private String proReviewOfClient;
    private String department;
    private String municipality;
    private Double proHourlyRate;
    private Double suggestedPrice;
    private Double counterOfferAmount;
    private String counterOfferSender;
    private String source;
    private String photos;
    private long updatedAt;

    public static JobDto from(Job job) {
        JobDto dto = new JobDto();
        dto.setId(job.getId());
        dto.setCategory(job.getCategory());
        dto.setTitle(job.getTitle() == null ? "" : job.getTitle());
        dto.setDescription(job.getDescription() == null ? "" : job.getDescription());
        dto.setBudgetMin(job.getBudgetMin());
        dto.setAddress(job.getAddress() == null ? "" : job.getAddress());
        dto.setLatitude(job.getLatitude());
        dto.setLongitude(job.getLongitude());
        dto.setStatus(job.getStatus());
        dto.setClientId(job.getClientId());
        dto.setClientName(job.getClientName());
        dto.setProfessionalId(job.getProfessionalId());
        dto.setProfessionalName(job.getProfessionalName());
        dto.setFinalPrice(job.getFinalPrice());
        dto.setCreatedAt(job.getCreatedAt());
        dto.setCompletionRating(job.getCompletionRating());
        dto.setCompletionReview(job.getCompletionReview());
        dto.setClientRatingOfPro(job.getClientRatingOfPro());
        dto.setClientReviewOfPro(job.getClientReviewOfPro());
        dto.setProRatingOfClient(job.getProRatingOfClient());
        dto.setProReviewOfClient(job.getProReviewOfClient());
        dto.setDepartment(job.getDepartment());
        dto.setMunicipality(job.getMunicipality());
        dto.setProHourlyRate(job.getProHourlyRate());
        dto.setSuggestedPrice(job.getSuggestedPrice());
        dto.setCounterOfferAmount(job.getCounterOfferAmount());
        dto.setCounterOfferSender(job.getCounterOfferSender());
        dto.setSource(job.getSource());
        dto.setPhotos(job.getPhotos());
        dto.setUpdatedAt(job.getUpdatedAt());
        return dto;
    }
}

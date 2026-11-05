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

    public String id;
    public String category;
    public String title;
    public String description;
    public double budgetMin;
    public String address;
    public double latitude;
    public double longitude;
    public String status;
    public String clientId;
    public String clientName;
    public String professionalId;
    public String professionalName;
    public Double finalPrice;
    public long createdAt;
    public Float completionRating;
    public String completionReview;
    public Float clientRatingOfPro;
    public String clientReviewOfPro;
    public Float proRatingOfClient;
    public String proReviewOfClient;
    public String department;
    public String municipality;
    public Double proHourlyRate;
    public Double suggestedPrice;
    public Double counterOfferAmount;
    public String counterOfferSender;
    public String source;
    public String photos;
    public long updatedAt;

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


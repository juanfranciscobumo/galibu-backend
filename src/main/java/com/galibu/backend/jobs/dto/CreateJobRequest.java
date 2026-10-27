package com.galibu.backend.jobs.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class CreateJobRequest {

    @Size(max = 64, message = "ID inválido")
    private String id;

    @Size(max = 80)
    private String category = "";

    @NotBlank
    @Size(max = 200)
    private String title;

    @Size(max = 5000)
    private String description;

    @Min(0)
    private double budgetMin = 0.0;

    @Size(max = 255)
    private String address;

    @Size(max = 80)
    private String department;

    @Size(max = 80)
    private String municipality;

    private double latitude = 0.0;

    private double longitude = 0.0;

    @Size(max = 64)
    private String professionalId;

    @Size(max = 120)
    private String professionalName;

    @Size(max = 20)
    private String source;

    @Size(max = 200000)
    private String photos;

    private Double proHourlyRate;

    @DecimalMin(value = "0.0", message = "El precio sugerido no puede ser negativo")
    private Double suggestedPrice;
}

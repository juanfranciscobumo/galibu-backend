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
    public String id;

    @Size(max = 80)
    public String category = "";

    @NotBlank
    @Size(max = 200)
    public String title;

    @Size(max = 5000)
    public String description;

    @Min(0)
    public double budgetMin = 0.0;

    @Size(max = 255)
    public String address;

    @Size(max = 80)
    public String department;

    @Size(max = 80)
    public String municipality;

    public double latitude = 0.0;

    public double longitude = 0.0;

    @Size(max = 64)
    public String professionalId;

    @Size(max = 120)
    public String professionalName;

    @Size(max = 20)
    public String source;

    @Size(max = 200000)
    public String photos;

    public Double proHourlyRate;

    @DecimalMin(value = "0.0", message = "El precio sugerido no puede ser negativo")
    public Double suggestedPrice;
}


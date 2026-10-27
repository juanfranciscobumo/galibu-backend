package com.galibu.backend.jobs.dto;

import jakarta.validation.constraints.DecimalMin;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class CounterRequest {

    @DecimalMin(value = "1.0", message = "El monto debe ser mayor a 0")
    private double amount;
}

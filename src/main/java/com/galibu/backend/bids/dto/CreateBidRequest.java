package com.galibu.backend.bids.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class CreateBidRequest {

    @DecimalMin(value = "1.0", message = "El monto debe ser mayor a 0")
    private double amount;

    @Size(max = 2000)
    private String comment;

    private int durationHours = 1;
}

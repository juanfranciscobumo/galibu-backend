package com.galibu.backend.jobs.dto;

import jakarta.validation.constraints.DecimalMin;

/**
 * Propuesta de precio enviada desde la tarjeta "Acordar precio" del chat.
 */
@lombok.AllArgsConstructor
@lombok.Getter
@lombok.NoArgsConstructor
@lombok.Setter
public class PriceProposalRequest {

    @DecimalMin(value = "1.0", message = "El monto debe ser mayor a 0")
    public double amount;
}

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
    public double amount;

    @Size(max = 2000)
    public String comment;

    public int durationHours = 1;

    /** ESTIMATED (estimada), AFTER_VISIT (tras visita) o FIXED (cerrada). */
    public String quoteType;

    /** La cotizacion requiere visita tecnica. */
    public Boolean visitRequired;

    /** Costo informativo de la visita tecnica (opcional). */
    public Double visitFee;

    /** El precio incluye materiales. */
    public Boolean materialsIncluded;

    /** Costo estimado de materiales (opcional, informativo). */
    public Double materialsCost;
}

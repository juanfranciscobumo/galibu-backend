package com.galibu.backend.wallet.dto;

import jakarta.validation.constraints.DecimalMin;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class RechargeRequest {

    @DecimalMin(value = "1.0", message = "El monto debe ser mayor a cero")
    private double amount;
}

package com.galibu.backend.subscription.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class SubscriptionCheckoutRequest {

    /** MONTHLY o YEARLY. */
    @NotBlank(message = "El plan es obligatorio")
    public String plan;
}

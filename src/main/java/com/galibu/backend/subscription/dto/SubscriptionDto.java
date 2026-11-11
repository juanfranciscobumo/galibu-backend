package com.galibu.backend.subscription.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Estado del plan del profesional y de sus servicios gratuitos. Es la fuente
 * que usa la app para mostrar el plan y decidir si debe abrir el paywall.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class SubscriptionDto {

    public String plan;
    public String status;
    public Long expiresAt;
    public boolean active;
    public boolean canWork;
    public int freeServicesTotal;
    public int freeServicesUsed;
    public int freeServicesRemaining;
    public double monthlyPrice;
    public double yearlyPrice;
}

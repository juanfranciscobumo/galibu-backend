package com.galibu.backend.wallet.bold.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class BoldCheckoutResponse {

    private final String orderId;
    private final String paymentLink;
    private final String checkoutUrl;
    private final double amount;
    private final String status;
}

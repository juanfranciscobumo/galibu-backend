package com.galibu.backend.wallet.bold.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class BoldOrderStatusResponse {

    private final String orderId;
    private final String status;
    private final double amount;
    private final double balance;
    private final String transactionId;
    private final String paymentMethod;
}

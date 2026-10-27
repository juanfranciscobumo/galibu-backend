package com.galibu.backend.wallet.bold.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class BoldOrderDto {
    private String orderId;
    private double amount;
    private String status;
    private long createdAt;
    private Long paidAt;
}
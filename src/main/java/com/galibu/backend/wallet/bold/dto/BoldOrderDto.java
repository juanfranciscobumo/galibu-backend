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
    public String orderId;
    public double amount;
    public String status;
    public long createdAt;
    public Long paidAt;
}

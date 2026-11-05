package com.galibu.backend.wallet.dto;

import com.galibu.backend.wallet.WalletTransaction;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class WalletTransactionDto {

    public String id;
    public String type;
    public double amount;
    public double balanceAfter;
    public String jobId;
    public String description;
    public long createdAt;

    public static WalletTransactionDto from(WalletTransaction tx) {
        WalletTransactionDto dto = new WalletTransactionDto();
        dto.setId(tx.getId());
        dto.setType(tx.getType());
        dto.setAmount(tx.getAmount());
        dto.setBalanceAfter(tx.getBalanceAfter());
        dto.setJobId(tx.getJobId());
        dto.setDescription(tx.getDescription());
        dto.setCreatedAt(tx.getCreatedAt());
        return dto;
    }
}


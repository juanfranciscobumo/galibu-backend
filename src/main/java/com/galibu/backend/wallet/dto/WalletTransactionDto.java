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

    private String id;
    private String type;
    private double amount;
    private double balanceAfter;
    private String jobId;
    private String description;
    private long createdAt;

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

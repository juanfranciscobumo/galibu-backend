package com.galibu.backend.wallet;

import com.galibu.backend.wallet.dto.RechargeRequest;
import com.galibu.backend.wallet.dto.WalletTransactionDto;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/wallet")
public class WalletController {

    private final WalletService service;

    public WalletController(WalletService service) {
        this.service = service;
    }

    @PostMapping("/recharge")
    public WalletTransactionDto recharge(Authentication auth, @Valid @RequestBody RechargeRequest req) {
        return service.recharge(auth.getName(), req.getAmount());
    }

    @GetMapping("/transactions")
    public List<WalletTransactionDto> transactions(Authentication auth) {
        return service.history(auth.getName());
    }
}

package com.galibu.backend.wallet;

import com.galibu.backend.wallet.dto.WalletTransactionDto;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/wallet")
public class WalletController {

    private static final Logger log = LoggerFactory.getLogger(WalletController.class);

    private final WalletService service;

    public WalletController(WalletService service) {
        this.service = service;
    }

    /**
     * Historial de la billetera. La recarga directa sin pasarela se eliminó:
     * cualquier abono entra únicamente por un pago confirmado en Bold.
     */
    @GetMapping("/transactions")
    public List<WalletTransactionDto> transactions(Authentication auth) {
        log.debug("wallet transactions userId={}", auth.getName());
        return service.history(auth.getName());
    }
}

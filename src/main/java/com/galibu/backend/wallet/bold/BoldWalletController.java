package com.galibu.backend.wallet.bold;

import com.galibu.backend.wallet.bold.dto.BoldCheckoutResponse;
import com.galibu.backend.wallet.bold.dto.BoldOrderDto;
import com.galibu.backend.wallet.bold.dto.BoldOrderStatusResponse;
import com.galibu.backend.wallet.dto.RechargeRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/wallet/bold")
public class BoldWalletController {

    private final BoldPaymentService service;

    public BoldWalletController(BoldPaymentService service) {
        this.service = service;
    }

    /** Genera el link de pago de Bold para recargar la billetera. */
    @PostMapping("/checkout")
    public BoldCheckoutResponse checkout(
        Authentication auth,
        @Valid @RequestBody RechargeRequest request
    ) {
        return service.createCheckout(auth.getName(), request.getAmount());
    }

    /** Consulta la orden y acredita el saldo cuando Bold confirma el pago. */
    @GetMapping("/orders/{orderId}")
    public BoldOrderStatusResponse status(
        Authentication auth,
        @PathVariable String orderId
    ) {
        return service.status(auth.getName(), orderId);
    }

    /** Lista las órdenes de pago del usuario (pendientes y completadas). */
    @GetMapping("/orders")
    public java.util.List<BoldOrderDto> orders(Authentication auth) {
        return service.listOrders(auth.getName());
    }

    /** Notificación de Bold. Se autentica con la firma HMAC, no con JWT. */
    @PostMapping("/webhook")
    public ResponseEntity<Void> webhook(
        @RequestBody(required = false) String body,
        @RequestHeader(value = "x-bold-signature", required = false) String signature
    ) {
        service.handleWebhook(body == null ? "" : body, signature);
        return ResponseEntity.ok().build();
    }
}

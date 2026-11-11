package com.galibu.backend.subscription;

import com.galibu.backend.subscription.dto.SubscriptionCheckoutRequest;
import com.galibu.backend.subscription.dto.SubscriptionDto;
import com.galibu.backend.wallet.bold.BoldPaymentService;
import com.galibu.backend.wallet.bold.dto.BoldCheckoutResponse;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/subscription")
public class SubscriptionController {

    private static final Logger log = LoggerFactory.getLogger(SubscriptionController.class);

    private final SubscriptionService subscriptions;
    private final BoldPaymentService boldPayments;

    public SubscriptionController(SubscriptionService subscriptions, BoldPaymentService boldPayments) {
        this.subscriptions = subscriptions;
        this.boldPayments = boldPayments;
    }

    /** Plan vigente, servicios gratis usados y precios. */
    @GetMapping("/me")
    public SubscriptionDto me(Authentication auth) {
        log.debug("subscription me userId={}", auth.getName());
        return subscriptions.me(auth.getName());
    }

    /** Crea el link de pago de Bold para activar o renovar Galibu Pro. */
    @PostMapping("/checkout")
    public BoldCheckoutResponse checkout(
        Authentication auth,
        @Valid @RequestBody SubscriptionCheckoutRequest request
    ) {
        String plan = request.getPlan();
        double amount = SubscriptionService.priceFor(plan);
        log.info("subscription checkout userId={} plan={} amount={}",
            auth.getName(), plan, amount);
        return boldPayments.createCheckout(auth.getName(), amount, "SUBSCRIPTION", plan);
    }

    /** Cancela la renovación; el acceso ya pagado sigue hasta el vencimiento. */
    @PostMapping("/cancel")
    public SubscriptionDto cancel(Authentication auth) {
        log.info("subscription cancel userId={}", auth.getName());
        return subscriptions.cancel(auth.getName());
    }
}

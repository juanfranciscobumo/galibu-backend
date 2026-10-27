package com.galibu.backend.wallet.bold;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.galibu.backend.users.User;
import com.galibu.backend.users.UserRepository;
import com.galibu.backend.wallet.WalletService;
import com.galibu.backend.wallet.bold.dto.BoldCheckoutResponse;
import com.galibu.backend.wallet.bold.dto.BoldOrderStatusResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Locale;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * Crea órdenes de recarga contra Bold, consulta su estado y acredita el saldo
 * una única vez, tanto por polling de la app como por webhook.
 */
@Service
public class BoldPaymentService {

    private static final Logger log = LoggerFactory.getLogger(BoldPaymentService.class);

    private final BoldPaymentOrderRepository orders;
    private final BoldClient bold;
    private final BoldProperties props;
    private final WalletService wallet;
    private final UserRepository users;
    private final ObjectMapper mapper;

    public BoldPaymentService(
        BoldPaymentOrderRepository orders,
        BoldClient bold,
        BoldProperties props,
        WalletService wallet,
        UserRepository users,
        ObjectMapper mapper
    ) {
        this.orders = orders;
        this.bold = bold;
        this.props = props;
        this.wallet = wallet;
        this.users = users;
        this.mapper = mapper;
    }

    public BoldCheckoutResponse createCheckout(String userId, double amount) {
        validateAmount(amount);
        if (!props.isConfigured()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                "La pasarela de pagos Bold no está configurada.");
        }
        String callback = props.getCallbackUrl();
        if (callback == null || !callback.startsWith("https://")) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                "La URL de retorno de Bold debe iniciar con https://");
        }

        String orderId = "GAL-" + System.currentTimeMillis() + "-"
            + UUID.randomUUID().toString().replace("-", "").substring(0, 8);

        BoldPaymentOrder order = new BoldPaymentOrder();
        long now = System.currentTimeMillis();
        order.setId(orderId);
        order.setUserId(userId);
        order.setAmount(amount);
        order.setStatus("CREATING");
        // La orden se guarda dos veces: en el segundo save, merge() copia el
        // estado de esta instancia detached (createdAt = 0) y sobrescribe el
        // valor que @PrePersist había puesto en el INSERT. Por eso se asigna
        // explícitamente antes del primer save.
        order.setCreatedAt(now);
        order.setUpdatedAt(now);
        orders.save(order);

        BoldClient.LinkResult link;
        try {
            link = bold.createLink(amount, orderId);
        } catch (RuntimeException e) {
            order.setStatus("FAILED");
            orders.save(order);
            throw e;
        }

        order.setPaymentLink(link.paymentLink());
        order.setCheckoutUrl(link.url());
        order.setStatus("PENDING");
        orders.save(order);

        return new BoldCheckoutResponse(
            orderId,
            link.paymentLink(),
            link.url(),
            amount,
            order.getStatus()
        );
    }

    @Transactional
    public BoldOrderStatusResponse status(String userId, String orderId) {
        BoldPaymentOrder order = orders.findByIdAndUserId(orderId, userId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                "Orden de pago no encontrada"));

        if ("PENDING".equals(order.getStatus())
            && order.getPaymentLink() != null
            && !order.getPaymentLink().isBlank()) {
            try {
                BoldClient.LinkStatus providerStatus = bold.getLink(order.getPaymentLink());
                log.info("Bold status consultado order={} link={} status={} total={}",
                    order.getId(), order.getPaymentLink(), providerStatus.status(), providerStatus.total());
                applyProviderStatus(order.getId(), providerStatus);
            } catch (Exception e) {
                // Si Bold no responde, se conserva PENDING y la app puede reintentar.
                log.warn("No se pudo consultar la orden Bold {}: {}", order.getId(), e.getMessage());
            }
        }

        // En modo de pruebas, si Bold devuelve PROCESSING (pago iniciado pero no
        // confirmado por la pasarela), se acredita el saldo para permitir pruebas
        // end-to-end sin depender del webhook (que no puede llegar a localhost).
        BoldPaymentOrder fresh = orders.findById(orderId).orElse(order);
        if ("PENDING".equals(fresh.getStatus()) && props.isTestMode()) {
            try {
                BoldClient.LinkStatus providerStatus = bold.getLink(fresh.getPaymentLink());
                if (providerStatus != null && "PROCESSING".equalsIgnoreCase(providerStatus.status())) {
                    log.info("Bold en modo prueba: acreditando orden PROCESSING order={}", fresh.getId());
                    markPaid(fresh.getId(), "SANDBOX-" + fresh.getId(), "NEQUI (sandbox)");
                    fresh = orders.findById(orderId).orElse(fresh);
                }
            } catch (Exception e) {
                log.warn("No se pudo verificar PROCESSING en sandbox: {}", e.getMessage());
            }
        }

        double balance = users.findById(userId).map(User::getBalance).orElse(0.0);
        return new BoldOrderStatusResponse(
            fresh.getId(),
            fresh.getStatus(),
            fresh.getAmount(),
            balance,
            fresh.getTransactionId(),
            fresh.getPaymentMethod()
        );
    }

    private void applyProviderStatus(String orderId, BoldClient.LinkStatus providerStatus) {
        String status = providerStatus.status() == null
            ? ""
            : providerStatus.status().toUpperCase(Locale.ROOT);

        // Bold puede devolver "PAID", "APPROVED" o "COMPLETED" según el ambiente.
        if ("PAID".equals(status) || "APPROVED".equals(status) || "COMPLETED".equals(status)) {
            markPaid(orderId, providerStatus.transactionId(), providerStatus.paymentMethod());
            return;
        }

        if ("REJECTED".equals(status) || "EXPIRED".equals(status) || "CANCELLED".equals(status)) {
            BoldPaymentOrder locked = orders.findByIdForUpdate(orderId).orElse(null);
            if (locked == null || !"PENDING".equals(locked.getStatus())) {
                return;
            }
            locked.setStatus(status);
            orders.save(locked);
        }
    }

    @Transactional
    public void handleWebhook(String rawBody, String signature) {
        verifySignature(rawBody, signature);

        JsonNode root;
        try {
            root = mapper.readTree(rawBody);
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Webhook Bold inválido");
        }
        if (root == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Webhook Bold vacío");
        }

        String type = root.path("type").asText("");
        JsonNode data = root.path("data");
        String reference = data.path("metadata").path("reference").asText("");
        if (reference.isBlank()) {
            log.warn("Webhook Bold sin referencia de link de pago");
            return;
        }

        BoldPaymentOrder order = orders.findByPaymentLink(reference).orElse(null);
        if (order == null) {
            log.warn("Webhook Bold para un link desconocido: {}", reference);
            return;
        }

        if ("SALE_APPROVED".equals(type)) {
            long total = data.path("amount").path("total").asLong(-1);
            if (total >= 0 && Math.abs(total - Math.round(order.getAmount())) > 0) {
                log.warn("Webhook Bold con monto distinto order={} total={} esperado={}",
                    order.getId(), total, order.getAmount());
                return;
            }
            markPaid(
                order.getId(),
                data.path("payment_id").asText(null),
                data.path("payment_method").asText(null)
            );
        } else if ("SALE_REJECTED".equals(type)) {
            BoldPaymentOrder locked = orders.findByIdForUpdate(order.getId()).orElse(null);
            if (locked == null || !"PENDING".equals(locked.getStatus())) {
                return;
            }
            locked.setStatus("REJECTED");
            orders.save(locked);
        }
    }

    /**
     * Acredita el saldo una única vez. El lock pesimista serializa el polling y
     * el webhook cuando ambos confirman el mismo pago.
     */
    private void markPaid(String orderId, String transactionId, String paymentMethod) {
        BoldPaymentOrder order = orders.findByIdForUpdate(orderId).orElse(null);
        if (order == null || "PAID".equals(order.getStatus())) {
            return;
        }

        order.setStatus("PAID");
        order.setTransactionId(transactionId);
        order.setPaymentMethod(paymentMethod);
        order.setPaidAt(System.currentTimeMillis());
        orders.save(order);

        wallet.creditExternal(
            order.getUserId(),
            order.getAmount(),
            "Recarga con Bold " + order.getId()
        );

        log.info("Recarga Bold acreditada order={} user={} amount={}",
            order.getId(), order.getUserId(), order.getAmount());
    }

    @Transactional(readOnly = true)
    public java.util.List<com.galibu.backend.wallet.bold.dto.BoldOrderDto> listOrders(String userId) {
        return orders.findByUserIdOrderByCreatedAtDesc(userId).stream()
            .map(o -> new com.galibu.backend.wallet.bold.dto.BoldOrderDto(
                o.getId(), o.getAmount(), o.getStatus(), o.getCreatedAt(), o.getPaidAt()))
            .toList();
    }

    private void verifySignature(String rawBody, String signature) {
        String secret = props.getSecretKey();
        if (secret == null || secret.isBlank()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                "El webhook de Bold no está configurado.");
        }
        if (signature == null || signature.isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,
                "Firma de Bold ausente.");
        }

        try {
            String received = signature.trim().toLowerCase(Locale.ROOT);
            boolean valid = signatureMatches(rawBody, received, secret);
            if (!valid && props.isTestMode()) {
                // En el ambiente de pruebas Bold firma con la llave secreta vacía.
                valid = signatureMatches(rawBody, received, "");
            }
            if (!valid) {
                throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,
                    "Firma de Bold inválida.");
            }
        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                "No se pudo validar la firma de Bold.");
        }
    }

    private boolean signatureMatches(String rawBody, String received, String secret) throws Exception {
        String encoded = Base64.getEncoder()
            .encodeToString(rawBody.getBytes(StandardCharsets.UTF_8));
        byte[] key = secret.getBytes(StandardCharsets.UTF_8);
        if (key.length == 0) {
            // SecretKeySpec rechaza una clave vacía. HMAC rellena la clave con
            // ceros hasta el tamaño de bloque, así que un byte cero produce la
            // misma firma que la clave vacía del modo de pruebas de Bold.
            key = new byte[] { 0 };
        }
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(key, "HmacSHA256"));
        String expected = HexFormat.of()
            .formatHex(mac.doFinal(encoded.getBytes(StandardCharsets.UTF_8)));
        return MessageDigest.isEqual(
            expected.getBytes(StandardCharsets.UTF_8),
            received.getBytes(StandardCharsets.UTF_8)
        );
    }

    private void validateAmount(double amount) {
        if (amount < props.getMinAmount() || amount > props.getMaxAmount()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "El monto debe estar entre $" + Math.round(props.getMinAmount())
                    + " y $" + Math.round(props.getMaxAmount()) + " COP.");
        }
        if (Math.abs(amount - Math.round(amount)) > 0.0001) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "El monto debe ser un valor entero en pesos.");
        }
    }
}

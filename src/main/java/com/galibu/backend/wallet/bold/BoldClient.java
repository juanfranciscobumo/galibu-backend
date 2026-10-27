package com.galibu.backend.wallet.bold;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.server.ResponseStatusException;

/**
 * Cliente HTTP de la API de Links de Pago de Bold.
 *
 * La llave de identidad viaja en la cabecera Authorization y nunca se registra
 * en logs. El link generado se consulta por su id LNK_* para confirmar pagos.
 */
@Component
public class BoldClient {

    private static final Logger log = LoggerFactory.getLogger(BoldClient.class);

    private final RestClient rest;
    private final BoldProperties props;

    public BoldClient(RestClient.Builder builder, BoldProperties props) {
        this.props = props;
        String baseUrl = props.getApiUrl() == null ? "" : props.getApiUrl().trim();
        while (baseUrl.endsWith("/")) {
            baseUrl = baseUrl.substring(0, baseUrl.length() - 1);
        }
        this.rest = builder.baseUrl(baseUrl).build();
    }

    public LinkResult createLink(double amount, String reference) {
        requireConfigured();

        String description = "Recarga Galibu " + reference;
        if (description.length() > 100) {
            description = description.substring(0, 100);
        }

        Map<String, Object> money = new LinkedHashMap<>();
        money.put("currency", "COP");
        money.put("total_amount", Math.round(amount));

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("amount_type", "CLOSE");
        body.put("amount", money);
        body.put("description", description);
        body.put("callback_url", props.getCallbackUrl());
        body.put("payment_methods",
            List.of("CREDIT_CARD", "PSE", "BOTON_BANCOLOMBIA", "NEQUI"));

        try {
            BoldLinkResponse response = rest.post()
                .uri("/online/link/v1")
                .header("Authorization", "x-api-key " + props.getApiKey())
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .body(BoldLinkResponse.class);

            if (response == null || response.payload() == null
                || response.payload().url() == null
                || response.payload().url().isBlank()) {
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "Bold no devolvió un link de pago válido.");
            }

            return new LinkResult(response.payload().paymentLink(), response.payload().url());
        } catch (RestClientResponseException e) {
            log.warn("Bold createLink falló status={} body={}",
                e.getStatusCode().value(), abbreviate(e.getResponseBodyAsString()));
            int status = e.getStatusCode().value();
            String message = (status == 401 || status == 403)
                ? "Las llaves de Bold no son válidas o están inactivas."
                : "Bold no pudo generar el link de pago.";
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, message);
        }
    }

    public LinkStatus getLink(String paymentLink) {
        requireConfigured();
        try {
            BoldLinkStatusResponse response = rest.get()
                .uri("/online/link/v1/{link}", paymentLink)
                .header("Authorization", "x-api-key " + props.getApiKey())
                .retrieve()
                .body(BoldLinkStatusResponse.class);

            if (response == null) {
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "Bold no devolvió el estado del pago.");
            }
            return new LinkStatus(
                response.status(),
                response.total(),
                response.transactionId(),
                response.paymentMethod()
            );
        } catch (RestClientResponseException e) {
            if (e.getStatusCode().value() == 404) {
                // Bold elimina los links vencidos: ya no se pueden cobrar ni
                // consultar, así que la orden queda expirada de forma terminal.
                log.info("Bold getLink: el link {} ya no existe; se marca como EXPIRED",
                    paymentLink);
                return new LinkStatus("EXPIRED", null, null, null);
            }
            log.warn("Bold getLink falló status={} body={}",
                e.getStatusCode().value(), abbreviate(e.getResponseBodyAsString()));
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                "No se pudo consultar el estado del pago en Bold.");
        }
    }

    private void requireConfigured() {
        if (!props.isConfigured()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                "La pasarela de pagos Bold no está configurada.");
        }
    }

    private static String abbreviate(String value) {
        if (value == null) {
            return "";
        }
        return value.length() <= 300 ? value : value.substring(0, 300) + "...";
    }

    public record LinkResult(String paymentLink, String url) {
    }

    public record LinkStatus(String status, Long total, String transactionId, String paymentMethod) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record BoldLinkResponse(Payload payload, List<Object> errors) {

        @JsonIgnoreProperties(ignoreUnknown = true)
        public record Payload(
            @JsonProperty("payment_link") String paymentLink,
            String url
        ) {
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record BoldLinkStatusResponse(
        String status,
        Long total,
        @JsonProperty("transaction_id") String transactionId,
        @JsonProperty("payment_method") String paymentMethod
    ) {
    }
}

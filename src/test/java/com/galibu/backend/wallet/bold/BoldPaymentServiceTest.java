package com.galibu.backend.wallet.bold;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.galibu.backend.users.UserRepository;
import com.galibu.backend.wallet.WalletService;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Optional;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class BoldPaymentServiceTest {

    @Mock
    private BoldPaymentOrderRepository orders;

    @Mock
    private BoldClient bold;

    @Mock
    private WalletService wallet;

    @Mock
    private UserRepository users;

    private BoldPaymentService service;

    @BeforeEach
    void setUp() {
        BoldProperties props = new BoldProperties();
        props.setSecretKey("configured-secret");
        props.setTestMode(true);
        service = new BoldPaymentService(
            orders, bold, props, wallet, users, new ObjectMapper());
    }

    @Test
    void acceptsEmptySecretSignatureInTestMode() throws Exception {
        String body = "{\"type\":\"SALE_APPROVED\",\"data\":{\"metadata\":{\"reference\":\"LNK_TEST\"}}}";
        when(orders.findByPaymentLink("LNK_TEST")).thenReturn(Optional.empty());

        assertDoesNotThrow(() -> service.handleWebhook(body, sign(body, "")));
    }

    @Test
    void rejectsInvalidSignature() throws Exception {
        String body = "{\"type\":\"SALE_APPROVED\",\"data\":{\"metadata\":{\"reference\":\"LNK_TEST\"}}}";

        ResponseStatusException error = assertThrows(ResponseStatusException.class,
            () -> service.handleWebhook(body, "firma-invalida"));

        assertEquals(HttpStatus.UNAUTHORIZED, error.getStatusCode());
    }

    private static String sign(String body, String secret) throws Exception {
        String encoded = Base64.getEncoder()
            .encodeToString(body.getBytes(StandardCharsets.UTF_8));
        byte[] key = secret.getBytes(StandardCharsets.UTF_8);
        if (key.length == 0) {
            key = new byte[] { 0 };
        }
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(key, "HmacSHA256"));
        return HexFormat.of().formatHex(
            mac.doFinal(encoded.getBytes(StandardCharsets.UTF_8)));
    }
}

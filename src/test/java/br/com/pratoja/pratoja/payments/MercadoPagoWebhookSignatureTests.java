package br.com.pratoja.pratoja.payments;

import org.junit.jupiter.api.Test;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

class MercadoPagoWebhookSignatureTests {
    private static final String SECRET = "segredo-de-teste";
    private static final String REQUEST_ID = "req-123";
    private static final String DATA_ID = "987654321";

    private String header(long ts, String dataId, String secret) {
        String manifest = "id:" + dataId + ";request-id:" + REQUEST_ID + ";ts:" + ts + ";";
        return "ts=" + ts + ",v1=" + hmac(manifest, secret);
    }

    private String hmac(String message, String secret) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] hash = mac.doFinal(message.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte b : hash) hex.append(String.format("%02x", b));
            return hex.toString();
        } catch (Exception e) { throw new IllegalStateException(e); }
    }

    @Test
    void validSignaturePasses() {
        long now = Instant.now().getEpochSecond();
        assertTrue(MercadoPagoWebhookSignature.valid(header(now, DATA_ID, SECRET), REQUEST_ID, DATA_ID, SECRET));
    }

    @Test
    void wrongSecretFails() {
        long now = Instant.now().getEpochSecond();
        assertFalse(MercadoPagoWebhookSignature.valid(header(now, DATA_ID, "outro-segredo"), REQUEST_ID, DATA_ID, SECRET));
    }

    @Test
    void tamperedDataIdFails() {
        long now = Instant.now().getEpochSecond();
        assertFalse(MercadoPagoWebhookSignature.valid(header(now, "111", SECRET), REQUEST_ID, DATA_ID, SECRET));
    }

    @Test
    void oldTimestampFails() {
        long old = Instant.now().getEpochSecond() - 3600;
        assertFalse(MercadoPagoWebhookSignature.valid(header(old, DATA_ID, SECRET), REQUEST_ID, DATA_ID, SECRET));
    }

    @Test
    void missingPartsFail() {
        assertFalse(MercadoPagoWebhookSignature.valid("ts=123", REQUEST_ID, DATA_ID, SECRET));
        assertFalse(MercadoPagoWebhookSignature.valid(null, REQUEST_ID, DATA_ID, SECRET));
        assertFalse(MercadoPagoWebhookSignature.valid("ts=abc,v1=00", REQUEST_ID, DATA_ID, SECRET));
        assertFalse(MercadoPagoWebhookSignature.valid("ts=1,v1=00", REQUEST_ID, DATA_ID, ""));
    }
}

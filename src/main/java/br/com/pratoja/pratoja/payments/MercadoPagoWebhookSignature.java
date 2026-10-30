package br.com.pratoja.pratoja.payments;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Locale;

/** Validacao da assinatura x-signature dos webhooks do Mercado Pago.
 *  Manifest: "id:{data.id};request-id:{x-request-id};ts:{ts};" com HMAC-SHA256 (segredo do webhook).
 *  Aceita janela de 5 minutos para o timestamp, limitando replay. */
public final class MercadoPagoWebhookSignature {
    private static final long TOLERANCE_SECONDS = 300;

    private MercadoPagoWebhookSignature() {}

    public static boolean valid(String signatureHeader, String requestId, String dataId, String secret) {
        return validAt(signatureHeader, requestId, dataId, secret, Instant.now().getEpochSecond());
    }

    static boolean validAt(String signatureHeader, String requestId, String dataId, String secret, long nowEpochSeconds) {
        if (signatureHeader == null || requestId == null || dataId == null || secret == null || secret.isBlank()) return false;
        String ts = null, v1 = null;
        for (String part : signatureHeader.split(",")) {
            String[] kv = part.trim().split("=", 2);
            if (kv.length != 2) continue;
            if (kv[0].equals("ts")) ts = kv[1];
            if (kv[0].equals("v1")) v1 = kv[1];
        }
        if (ts == null || v1 == null) return false;
        long timestamp;
        try { timestamp = Long.parseLong(ts); } catch (NumberFormatException e) { return false; }
        if (Math.abs(nowEpochSeconds - timestamp) > TOLERANCE_SECONDS) return false;
        String manifest = "id:" + dataId + ";request-id:" + requestId + ";ts:" + ts + ";";
        String expected = hmacSha256(manifest, secret);
        return MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8),
                v1.toLowerCase(Locale.ROOT).getBytes(StandardCharsets.UTF_8));
    }

    private static String hmacSha256(String message, String secret) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] hash = mac.doFinal(message.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(hash.length * 2);
            for (byte b : hash) hex.append(String.format("%02x", b));
            return hex.toString();
        } catch (Exception e) { throw new IllegalStateException(e); }
    }
}

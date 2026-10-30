package br.com.pratoja.pratoja.payments;

import java.math.BigDecimal;

/** Cliente HTTP minimo da API do Mercado Pago (sem SDK: poucos endpoints, contrato explicito). */
public interface MercadoPagoClient {

    record CreatePixPayment(String accessToken, BigDecimal amount, String description,
                            String payerEmail, String externalReference, String notificationUrl,
                            String idempotencyKey, String expiresAtIso) {}

    record CreateCardPayment(String accessToken, BigDecimal amount, String description,
                             String payerEmail, String externalReference, String notificationUrl,
                             String idempotencyKey, String cardToken, String paymentMethodId,
                             String issuerId, int installments) {}

    record MpPayment(Long id, String status, BigDecimal transactionAmount, String currencyId,
                     String externalReference, Long collectorId,
                     String qrCode, String qrCodeBase64, String ticketUrl) {}

    record MpToken(String accessToken, String refreshToken, long expiresInSeconds,
                   Long userId, String publicKey) {}

    MpPayment createPixPayment(CreatePixPayment request);

    MpPayment createCardPayment(CreateCardPayment request);

    /** Public key do vendedor (necessaria para tokenizar cartoes no front com MercadoPago.js). */
    String getPublicKey(String accessToken);

    MpPayment getPayment(String accessToken, long paymentId);

    MpToken exchangeCode(String code, String redirectUri);

    MpToken refreshToken(String refreshToken);
}

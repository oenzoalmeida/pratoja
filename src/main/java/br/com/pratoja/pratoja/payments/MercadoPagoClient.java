package br.com.pratoja.pratoja.payments;

import java.math.BigDecimal;

/** Cliente HTTP minimo da API do Mercado Pago (sem SDK: poucos endpoints, contrato explicito). */
public interface MercadoPagoClient {

    record CreatePixPayment(String accessToken, BigDecimal amount, String description,
                            String payerEmail, String externalReference, String notificationUrl,
                            String idempotencyKey) {}

    record MpPayment(Long id, String status, BigDecimal transactionAmount, String currencyId,
                     String externalReference, String qrCode, String qrCodeBase64, String ticketUrl) {}

    record MpToken(String accessToken, String refreshToken, long expiresInSeconds,
                   Long userId, String publicKey) {}

    MpPayment createPixPayment(CreatePixPayment request);

    MpPayment getPayment(String accessToken, long paymentId);

    MpToken exchangeCode(String code, String redirectUri);

    MpToken refreshToken(String refreshToken);
}

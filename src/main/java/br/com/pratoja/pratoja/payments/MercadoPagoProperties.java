package br.com.pratoja.pratoja.payments;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Configuracao da integracao Mercado Pago. Segredos chegam APENAS por variaveis de ambiente
 *  (nunca no banco nem no codigo). Sem client-id/secret configurados a integracao fica inativa
 *  e o checkout segue no modo demonstracao (confirmacao simulada). */
@Component
public class MercadoPagoProperties {
    @Value("${pratoja.mp.client-id:}") public String clientId;
    @Value("${pratoja.mp.client-secret:}") public String clientSecret;
    @Value("${pratoja.mp.webhook-secret:}") public String webhookSecret;
    @Value("${pratoja.mp.api-base-url:https://api.mercadopago.com}") public String apiBaseUrl;
    @Value("${pratoja.mp.auth-base-url:https://auth.mercadopago.com}") public String authBaseUrl;
    @Value("${pratoja.mp.default-mode:TEST}") public String defaultMode;
    @Value("${pratoja.public-base-url:http://localhost:8080}") public String publicBaseUrl;
    /** Chave AES-256 (base64, 32 bytes) para criptografar tokens OAuth das lojas no banco. */
    @Value("${pratoja.payments.crypto-key:}") public String cryptoKey;

    public boolean oauthConfigured() { return !clientId.isBlank() && !clientSecret.isBlank(); }
    public boolean webhookConfigured() { return !webhookSecret.isBlank(); }
    public boolean cryptoConfigured() { return !cryptoKey.isBlank(); }
}

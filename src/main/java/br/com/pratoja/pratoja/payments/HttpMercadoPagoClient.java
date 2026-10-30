package br.com.pratoja.pratoja.payments;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

/** Implementacao HTTP (java.net.http) da API do Mercado Pago.
 *  Base URL configuravel para permitir testes de ponta a ponta contra um servidor stub. */
@Component
public class HttpMercadoPagoClient implements MercadoPagoClient {
    private final MercadoPagoProperties props;
    private final ObjectMapper mapper = new ObjectMapper();
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

    public HttpMercadoPagoClient(MercadoPagoProperties props) { this.props = props; }

    @Override
    public MpPayment createPixPayment(CreatePixPayment request) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("transaction_amount", request.amount());
        body.put("description", request.description());
        body.put("payment_method_id", "pix");
        body.put("external_reference", request.externalReference());
        if (request.notificationUrl() != null) body.put("notification_url", request.notificationUrl());
        if (request.expiresAtIso() != null) body.put("date_of_expiration", request.expiresAtIso());
        body.put("payer", Map.of("email", request.payerEmail()));
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(props.apiBaseUrl + "/v1/payments"))
                .timeout(Duration.ofSeconds(20))
                .header("Authorization", "Bearer " + request.accessToken())
                .header("Content-Type", "application/json")
                .header("X-Idempotency-Key", request.idempotencyKey())
                .POST(HttpRequest.BodyPublishers.ofString(json(body)));
        return mapPayment(send(builder));
    }

    @Override
    public MpPayment createCardPayment(CreateCardPayment request) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("transaction_amount", request.amount());
        body.put("description", request.description());
        body.put("token", request.cardToken());
        body.put("payment_method_id", request.paymentMethodId());
        if (request.issuerId() != null) body.put("issuer_id", request.issuerId());
        body.put("installments", request.installments());
        body.put("external_reference", request.externalReference());
        if (request.notificationUrl() != null) body.put("notification_url", request.notificationUrl());
        body.put("payer", Map.of("email", request.payerEmail()));
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(props.apiBaseUrl + "/v1/payments"))
                .timeout(Duration.ofSeconds(20))
                .header("Authorization", "Bearer " + request.accessToken())
                .header("Content-Type", "application/json")
                .header("X-Idempotency-Key", request.idempotencyKey())
                .POST(HttpRequest.BodyPublishers.ofString(json(body)));
        return mapPayment(send(builder));
    }

    @Override
    public String getPublicKey(String accessToken) {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(props.apiBaseUrl + "/users/me"))
                .timeout(Duration.ofSeconds(20))
                .header("Authorization", "Bearer " + accessToken)
                .GET();
        JsonNode body = send(builder);
        return body.path("public_key").isMissingNode() ? null : body.path("public_key").asText(null);
    }

    @Override
    public MpPayment getPayment(String accessToken, long paymentId) {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(props.apiBaseUrl + "/v1/payments/" + paymentId))
                .timeout(Duration.ofSeconds(20))
                .header("Authorization", "Bearer " + accessToken)
                .GET();
        return mapPayment(send(builder));
    }

    @Override
    public MpToken exchangeCode(String code, String redirectUri) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("client_id", props.clientId);
        body.put("client_secret", props.clientSecret);
        body.put("grant_type", "authorization_code");
        body.put("code", code);
        body.put("redirect_uri", redirectUri);
        return mapToken(postForm("/oauth/token", body));
    }

    @Override
    public MpToken refreshToken(String refreshToken) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("client_id", props.clientId);
        body.put("client_secret", props.clientSecret);
        body.put("grant_type", "refresh_token");
        body.put("refresh_token", refreshToken);
        return mapToken(postForm("/oauth/token", body));
    }

    private JsonNode postForm(String path, Map<String, Object> fields) {
        StringBuilder form = new StringBuilder();
        fields.forEach((k, v) -> {
            if (!form.isEmpty()) form.append('&');
            form.append(k).append('=').append(java.net.URLEncoder.encode(String.valueOf(v), StandardCharsets.UTF_8));
        });
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(props.apiBaseUrl + path))
                .timeout(Duration.ofSeconds(20))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(form.toString()));
        return send(builder);
    }

    private JsonNode send(HttpRequest.Builder builder) {
        try {
            HttpResponse<String> response = http.send(builder.build(), HttpResponse.BodyHandlers.ofString());
            JsonNode body = response.statusCode() >= 200 && response.statusCode() < 300 ? mapper.readTree(response.body()) : null;
            if (body == null) {
                String detail = response.body() == null ? "" : response.body();
                // Nunca logar/relancar corpo com possivel token; a API do MP nao ecoa segredos nos erros, mas limitamos o tamanho.
                throw new MercadoPagoException("Mercado Pago respondeu HTTP " + response.statusCode() + ": " + abbreviate(detail));
            }
            return body;
        } catch (MercadoPagoException e) { throw e; }
        catch (Exception e) { throw new MercadoPagoException("Falha de comunicacao com o Mercado Pago.", e); }
    }

    private MpPayment mapPayment(JsonNode body) {
        JsonNode txData = body.path("point_of_interaction").path("transaction_data");
        return new MpPayment(
                body.path("id").asLong(),
                body.path("status").asText(""),
                body.path("transaction_amount").decimalValue(),
                body.path("currency_id").asText(""),
                body.path("external_reference").isNull() ? null : body.path("external_reference").asText(),
                body.path("collector_id").isMissingNode() ? null : body.path("collector_id").asLong(),
                txData.path("qr_code").isMissingNode() ? null : txData.path("qr_code").asText(null),
                txData.path("qr_code_base64").isMissingNode() ? null : txData.path("qr_code_base64").asText(null),
                txData.path("ticket_url").isMissingNode() ? null : txData.path("ticket_url").asText(null));
    }

    private MpToken mapToken(JsonNode body) {
        return new MpToken(
                body.path("access_token").asText(),
                body.path("refresh_token").asText(null),
                body.path("expires_in").asLong(0),
                body.path("user_id").isMissingNode() ? null : body.path("user_id").asLong(),
                body.path("public_key").isMissingNode() ? null : body.path("public_key").asText(null));
    }

    private String json(Map<String, Object> body) {
        try { return mapper.writeValueAsString(body); }
        catch (Exception e) { throw new IllegalStateException(e); }
    }

    private String abbreviate(String detail) { return detail.length() > 200 ? detail.substring(0, 200) : detail; }

    public static class MercadoPagoException extends RuntimeException {
        public MercadoPagoException(String message) { super(message); }
        public MercadoPagoException(String message, Throwable cause) { super(message, cause); }
    }
}

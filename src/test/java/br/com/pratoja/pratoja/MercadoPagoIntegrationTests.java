package br.com.pratoja.pratoja;

import br.com.pratoja.pratoja.domain.*;
import br.com.pratoja.pratoja.payments.PaymentSecretsService;
import br.com.pratoja.pratoja.repository.*;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Fluxo Pix Mercado Pago de ponta a ponta contra um servidor HTTP stub da API do MP:
 * checkout cria pagamento PENDING, tela gera QR via API, webhook assinado confirma,
 * duplicata nao reprocessa, valor divergente nao confirma, estorno cancela,
 * e loja sem conexao segue no modo simulado.
 */
@SpringBootTest
@AutoConfigureMockMvc
class MercadoPagoIntegrationTests {
    private static final String WEBHOOK_SECRET = "wh-test-secret";
    private static final String CRYPTO_KEY = "MDEyMzQ1Njc4OTAxMjM0NTY3ODkwMTIzNDU2Nzg5MDE="; // 32 bytes "0123...901"
    private static final String STORE_TOKEN = "TEST-ACCESS-TOKEN-LOJA";
    private static final AtomicLong paymentIds = new AtomicLong(50000);
    private static final Map<String, Map<String, Object>> stubPayments = new ConcurrentHashMap<>();
    private static final Map<String, String> lastAuthByPayment = new ConcurrentHashMap<>();
    private static HttpServer stub;

    @Autowired MockMvc mvc;
    @Autowired StoreRepository stores;
    @Autowired StorePaymentAccountRepository accounts;
    @Autowired OrderRepository orders;
    @Autowired PaymentRepository payments;
    @Autowired PaymentSecretsService secrets;
    @Autowired ProcessedWebhookEventRepository webhookEvents;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) throws IOException {
        stub = HttpServer.create(new InetSocketAddress(0), 0);
        stub.createContext("/v1/payments", exchange -> {
            if ("POST".equals(exchange.getRequestMethod())) {
                String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
                long id = paymentIds.incrementAndGet();
                String ref = extract(body, "external_reference");
                BigDecimal amount = new BigDecimal(extract(body, "transaction_amount"));
                Map<String, Object> state = new ConcurrentHashMap<>();
                state.put("status", "pending");
                state.put("amount", amount);
                state.put("ref", ref);
                stubPayments.put(String.valueOf(id), state);
                lastAuthByPayment.put(String.valueOf(id), exchange.getRequestHeaders().getFirst("Authorization"));
                respond(exchange, 201, paymentJson(id, "pending", amount, ref, true));
            } else {
                String path = exchange.getRequestURI().getPath();
                String id = path.substring(path.lastIndexOf('/') + 1);
                Map<String, Object> state = stubPayments.get(id);
                if (state == null) { respond(exchange, 404, "{\"error\":\"not_found\"}"); return; }
                lastAuthByPayment.put(id, exchange.getRequestHeaders().getFirst("Authorization"));
                respond(exchange, 200, paymentJson(Long.parseLong(id), (String) state.get("status"),
                        (BigDecimal) state.get("amount"), (String) state.get("ref"), false));
            }
        });
        stub.start();
        registry.add("pratoja.mp.api-base-url", () -> "http://localhost:" + stub.getAddress().getPort());
        registry.add("pratoja.mp.client-id", () -> "test-client-id");
        registry.add("pratoja.mp.client-secret", () -> "test-client-secret");
        registry.add("pratoja.mp.webhook-secret", () -> WEBHOOK_SECRET);
        registry.add("pratoja.payments.crypto-key", () -> CRYPTO_KEY);
        registry.add("pratoja.mp.default-mode", () -> "TEST");
    }

    @AfterAll
    static void stopStub() { if (stub != null) stub.stop(0); }

    private static String extract(String json, String field) {
        String marker = "\"" + field + "\":";
        int i = json.indexOf(marker);
        if (i < 0) return null;
        String rest = json.substring(i + marker.length()).trim();
        if (rest.startsWith("\"")) return rest.substring(1, rest.indexOf('"', 1));
        return rest.replaceAll("[,}].*$", "").trim();
    }

    private static String paymentJson(long id, String status, BigDecimal amount, String ref, boolean withQr) {
        StringBuilder json = new StringBuilder();
        json.append("{\"id\":").append(id).append(",\"status\":\"").append(status).append("\"")
            .append(",\"transaction_amount\":").append(amount)
            .append(",\"currency_id\":\"BRL\",\"external_reference\":\"").append(ref).append("\"");
        if (withQr) json.append(",\"point_of_interaction\":{\"transaction_data\":{")
            .append("\"qr_code\":\"000201STUBCOPIAECOLA").append(id)
            .append("\",\"qr_code_base64\":\"iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNk+M9QDwADhgGAWjR9awAAAABJRU5ErkJggg==\"")
            .append(",\"ticket_url\":\"https://mp.stub/ticket/").append(id).append("\"}}");
        return json.append('}').toString();
    }

    private static void respond(com.sun.net.httpserver.HttpExchange exchange, int code, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(code, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) { os.write(bytes); }
    }

    private String signatureHeader(String dataId, String requestId) {
        try {
            long ts = Instant.now().getEpochSecond();
            String manifest = "id:" + dataId + ";request-id:" + requestId + ";ts:" + ts + ";";
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(WEBHOOK_SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] hash = mac.doFinal(manifest.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte b : hash) hex.append(String.format("%02x", b));
            return "ts=" + ts + ",v1=" + hex;
        } catch (Exception e) { throw new IllegalStateException(e); }
    }

    private MvcResult postWebhook(String dataId, long eventId) throws Exception {
        String requestId = "req-" + eventId;
        return mvc.perform(post("/webhooks/mercadopago")
                        .param("type", "payment").param("data.id", dataId)
                        .header("x-signature", signatureHeader(dataId, requestId))
                        .header("x-request-id", requestId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"action\":\"payment.updated\",\"type\":\"payment\",\"id\":" + eventId + ",\"data\":{\"id\":\"" + dataId + "\"}}"))
                .andExpect(status().isOk())
                .andReturn();
    }

    private void connectStore() {
        Store store = stores.findBySlug("restaurante").orElseThrow();
        StorePaymentAccount account = accounts.findByStore_Id(store.getId()).orElseGet(StorePaymentAccount::new);
        account.setStore(store);
        account.setStatus(StorePaymentAccount.Status.CONNECTED);
        account.setMode(StorePaymentAccount.Mode.TEST);
        account.setMpUserId("123456789");
        account.setAccessTokenEnc(secrets.encrypt(STORE_TOKEN));
        accounts.save(account);
    }

    private Long placePixOrder() throws Exception {
        MvcResult added = mvc.perform(post("/loja/restaurante/api/cart/items").with(csrf())
                        .with(user("cliente@pratoja.com.br").roles("CUSTOMER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productId\":1,\"quantity\":1,\"optionIds\":[],\"notes\":\"\"}"))
                .andExpect(status().isOk()).andReturn();
        MockHttpSession session = (MockHttpSession) added.getRequest().getSession(false);
        MvcResult checkout = mvc.perform(post("/loja/restaurante/checkout").with(csrf())
                        .with(user("cliente@pratoja.com.br").roles("CUSTOMER")).session(session)
                        .param("fulfillment", "PICKUP").param("paymentMethod", "PIX"))
                .andExpect(status().is3xxRedirection()).andReturn();
        String location = checkout.getResponse().getRedirectedUrl();
        assertNotNull(location);
        assertTrue(location.endsWith("/pagamento"), "PIX em loja conectada deve ir para a tela de pagamento, foi: " + location);
        return Long.parseLong(location.replaceAll(".*/pedidos/(\\d+)/pagamento$", "$1"));
    }

    @Test
    void pixCheckoutCreatesQrAndWebhookConfirmsOnce() throws Exception {
        connectStore();
        Long orderId = placePixOrder();

        Payment payment = payments.findAll().stream().filter(p -> p.getOrder().getId().equals(orderId)).findFirst().orElseThrow();
        assertEquals(DomainTypes.PaymentStatus.PENDING, payment.getStatus());
        assertNotNull(payment.getReference());

        // Tela de pagamento gera a cobranca no MP (stub) e mostra o QR/copia-e-cola
        mvc.perform(get("/loja/restaurante/pedidos/" + orderId + "/pagamento")
                        .with(user("cliente@pratoja.com.br").roles("CUSTOMER")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("000201STUBCOPIAECOLA")))
                .andExpect(content().string(containsString("Ambiente de testes")));
        payment = payments.findById(payment.getId()).orElseThrow();
        assertNotNull(payment.getMpPaymentId());
        assertEquals("Bearer " + STORE_TOKEN, lastAuthByPayment.get(payment.getMpPaymentId()),
                "A cobranca deve ser criada com o token DA LOJA, nao da plataforma");

        // Webhook aprovado: assinatura valida + re-consulta a API + confere valor/moeda/referencia
        stubPayments.get(payment.getMpPaymentId()).put("status", "approved");
        postWebhook(payment.getMpPaymentId(), 900001L);

        payment = payments.findById(payment.getId()).orElseThrow();
        assertEquals(DomainTypes.PaymentStatus.CONFIRMED, payment.getStatus());
        assertNotNull(payment.getPaidAt());
        Order order = orders.findById(orderId).orElseThrow();
        assertEquals(DomainTypes.OrderStatus.CONFIRMED, order.getStatus());
        assertTrue(webhookEvents.existsByProviderAndEventKey("mercadopago", "900001"));

        // Reenvio do MESMO evento (idempotencia): segue confirmado, sem efeito adicional
        postWebhook(payment.getMpPaymentId(), 900001L);
        payment = payments.findById(payment.getId()).orElseThrow();
        assertEquals(DomainTypes.PaymentStatus.CONFIRMED, payment.getStatus());
        assertEquals(DomainTypes.OrderStatus.CONFIRMED, orders.findById(orderId).orElseThrow().getStatus());

        // Assinatura invalida: 403 e nenhum processamento
        mvc.perform(post("/webhooks/mercadopago")
                        .param("type", "payment").param("data.id", payment.getMpPaymentId())
                        .header("x-signature", "ts=1,v1=invalida")
                        .header("x-request-id", "req-x")
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void webhookWithMismatchedAmountDoesNotConfirm() throws Exception {
        connectStore();
        Long orderId = placePixOrder();
        mvc.perform(get("/loja/restaurante/pedidos/" + orderId + "/pagamento")
                        .with(user("cliente@pratoja.com.br").roles("CUSTOMER")))
                .andExpect(status().isOk());
        Payment payment = payments.findAll().stream().filter(p -> p.getOrder().getId().equals(orderId)).findFirst().orElseThrow();
        // Stub passa a relatar valor DIFERENTE do pedido: nao pode confirmar
        stubPayments.get(payment.getMpPaymentId()).put("amount", new BigDecimal("0.01"));
        stubPayments.get(payment.getMpPaymentId()).put("status", "approved");
        postWebhook(payment.getMpPaymentId(), 900002L);
        payment = payments.findById(payment.getId()).orElseThrow();
        assertEquals(DomainTypes.PaymentStatus.PENDING, payment.getStatus());
        assertEquals(DomainTypes.OrderStatus.RECEIVED, orders.findById(orderId).orElseThrow().getStatus());
    }

    @Test
    void refundCancelsPaymentAndOrder() throws Exception {
        connectStore();
        Long orderId = placePixOrder();
        mvc.perform(get("/loja/restaurante/pedidos/" + orderId + "/pagamento")
                        .with(user("cliente@pratoja.com.br").roles("CUSTOMER")))
                .andExpect(status().isOk());
        Payment payment = payments.findAll().stream().filter(p -> p.getOrder().getId().equals(orderId)).findFirst().orElseThrow();
        stubPayments.get(payment.getMpPaymentId()).put("status", "approved");
        postWebhook(payment.getMpPaymentId(), 900003L);
        assertEquals(DomainTypes.OrderStatus.CONFIRMED, orders.findById(orderId).orElseThrow().getStatus());
        // Evento NOVO (id diferente) com estorno: cancela pagamento e pedido
        stubPayments.get(payment.getMpPaymentId()).put("status", "refunded");
        postWebhook(payment.getMpPaymentId(), 900004L);
        payment = payments.findById(payment.getId()).orElseThrow();
        assertEquals(DomainTypes.PaymentStatus.CANCELLED, payment.getStatus());
        assertEquals(DomainTypes.OrderStatus.CANCELLED, orders.findById(orderId).orElseThrow().getStatus());
    }

    @Test
    void manualVerifySyncsApprovedPayment() throws Exception {
        connectStore();
        Long orderId = placePixOrder();
        MockHttpSession session = new MockHttpSession();
        mvc.perform(get("/loja/restaurante/pedidos/" + orderId + "/pagamento")
                        .with(user("cliente@pratoja.com.br").roles("CUSTOMER")).session(session))
                .andExpect(status().isOk());
        Payment payment = payments.findAll().stream().filter(p -> p.getOrder().getId().equals(orderId)).findFirst().orElseThrow();
        stubPayments.get(payment.getMpPaymentId()).put("status", "approved");
        mvc.perform(post("/loja/restaurante/pedidos/" + orderId + "/pagamento/verificar").with(csrf())
                        .with(user("cliente@pratoja.com.br").roles("CUSTOMER")).session(session))
                .andExpect(status().is3xxRedirection());
        assertEquals(DomainTypes.PaymentStatus.CONFIRMED, payments.findById(payment.getId()).orElseThrow().getStatus());
    }

    @Test
    void disconnectedStoreKeepsSimulatedConfirmation() throws Exception {
        Store store = stores.findBySlug("restaurante").orElseThrow();
        accounts.findByStore_Id(store.getId()).ifPresent(a -> { a.setStatus(StorePaymentAccount.Status.DISCONNECTED); accounts.save(a); });
        MvcResult added = mvc.perform(post("/loja/restaurante/api/cart/items").with(csrf())
                        .with(user("cliente@pratoja.com.br").roles("CUSTOMER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productId\":1,\"quantity\":1,\"optionIds\":[],\"notes\":\"\"}"))
                .andExpect(status().isOk()).andReturn();
        MockHttpSession session = (MockHttpSession) added.getRequest().getSession(false);
        MvcResult checkout = mvc.perform(post("/loja/restaurante/checkout").with(csrf())
                        .with(user("cliente@pratoja.com.br").roles("CUSTOMER")).session(session)
                        .param("fulfillment", "PICKUP").param("paymentMethod", "PIX"))
                .andExpect(status().is3xxRedirection()).andReturn();
        assertTrue(checkout.getResponse().getRedirectedUrl().endsWith("/acompanhar"),
                "Sem conexao MP, PIX segue simulado e vai ao acompanhamento");
        Long orderId = Long.parseLong(checkout.getResponse().getRedirectedUrl().replaceAll(".*/pedidos/(\\d+)/acompanhar$", "$1"));
        Payment payment = payments.findAll().stream().filter(p -> p.getOrder().getId().equals(orderId)).findFirst().orElseThrow();
        assertEquals(DomainTypes.PaymentStatus.CONFIRMED, payment.getStatus());
        assertNull(payment.getReference());
        connectStore(); // reestabelece para os demais testes
    }

    @Test
    void otherCustomerCannotOpenPaymentPage() throws Exception {
        connectStore();
        Long orderId = placePixOrder();
        mvc.perform(get("/loja/restaurante/pedidos/" + orderId + "/pagamento")
                        .with(user("outro@exemplo.com").roles("CUSTOMER")))
                .andExpect(status().is4xxClientError());
        // Webhook anonimo continua alcancavel (rota publica) mas exige assinatura: coberto no primeiro teste.
    }
}

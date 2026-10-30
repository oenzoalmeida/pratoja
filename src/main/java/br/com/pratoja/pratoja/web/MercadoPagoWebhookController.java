package br.com.pratoja.pratoja.web;

import br.com.pratoja.pratoja.payments.MercadoPagoProperties;
import br.com.pratoja.pratoja.payments.MercadoPagoWebhookService;
import br.com.pratoja.pratoja.payments.MercadoPagoWebhookSignature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/** Recebe webhooks do Mercado Pago. Defesas em camadas:
 *  1) segredo configurado, senao 503 (falha fechada);
 *  2) assinatura HMAC x-signature validada com janela de tempo;
 *  3) deduplicacao por chave unica (reenvio/duplicata = 200 sem reprocessar);
 *  4) estado SEMPRE re-consultado na API do MP com o token da loja dona do pagamento;
 *  5) valor/moeda/referencia conferidos com o pedido antes de confirmar. */
@RestController
public class MercadoPagoWebhookController {
    private final MercadoPagoProperties props;
    private final MercadoPagoWebhookService service;
    private final ObjectMapper mapper = new ObjectMapper();

    public MercadoPagoWebhookController(MercadoPagoProperties props, MercadoPagoWebhookService service) {
        this.props = props;
        this.service = service;
    }

    @PostMapping("/webhooks/mercadopago")
    ResponseEntity<Void> receive(@RequestHeader(name = "x-signature", required = false) String signature,
                                 @RequestHeader(name = "x-request-id", required = false) String requestId,
                                 @RequestParam(name = "type", required = false) String type,
                                 @RequestParam(name = "data.id", required = false) String dataId,
                                 @RequestBody(required = false) String body) {
        if (!props.webhookConfigured()) return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).build();
        String action = null;
        String eventId = null;
        if (body != null && !body.isBlank()) {
            try {
                JsonNode json = mapper.readTree(body);
                if (type == null && !json.path("type").isMissingNode()) type = json.path("type").asText(null);
                if (!json.path("action").isMissingNode()) action = json.path("action").asText(null);
                if (!json.path("id").isMissingNode()) eventId = json.path("id").asText(null);
                JsonNode data = json.path("data").path("id");
                if (dataId == null && !data.isMissingNode()) dataId = data.asText(null);
            } catch (Exception ignored) { /* corpo invalido: segue com os parametros de query */ }
        }
        if (type == null || dataId == null) return ResponseEntity.badRequest().build();
        if (!MercadoPagoWebhookSignature.valid(signature, requestId, dataId, props.webhookSecret))
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        String eventKey = eventId != null ? eventId
                : requestId != null ? "req:" + requestId
                : type + ":" + dataId + ":" + (action == null ? "" : action);
        service.process(eventKey, type, dataId);
        // 200 inclusive para pagamento desconhecido: evita reenvios infinitos de eventos que nao sao nossos.
        return ResponseEntity.ok().build();
    }
}

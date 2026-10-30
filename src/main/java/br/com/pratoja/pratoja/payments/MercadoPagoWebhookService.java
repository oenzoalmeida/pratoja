package br.com.pratoja.pratoja.payments;

import br.com.pratoja.pratoja.domain.ProcessedWebhookEvent;
import br.com.pratoja.pratoja.domain.Payment;
import br.com.pratoja.pratoja.repository.PaymentRepository;
import br.com.pratoja.pratoja.repository.ProcessedWebhookEventRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Processamento de webhooks do Mercado Pago com deduplicacao forte e reprocessamento seguro:
 *  o evento so e marcado DEPOIS do processamento bem-sucedido — falha de rede/API gera 500,
 *  o provedor reenvia e o evento e processado de verdade. Entregas simultaneas do mesmo evento
 *  convergem: a chave unica (provider, event_key) admite um unico registro e as mutacoes de
 *  estado sao idempotentes (confirmado/cancelado ja aplicado = sem efeito). */
@Service
public class MercadoPagoWebhookService {
    private static final String PROVIDER = "mercadopago";

    private final ProcessedWebhookEventRepository events;
    private final PaymentRepository payments;
    private final OrderPaymentService orderPayments;

    public MercadoPagoWebhookService(ProcessedWebhookEventRepository events, PaymentRepository payments,
                                     OrderPaymentService orderPayments) {
        this.events = events;
        this.payments = payments;
        this.orderPayments = orderPayments;
    }

    public enum Outcome { PROCESSED, DUPLICATE, UNKNOWN_PAYMENT }

    public Outcome process(String eventKey, String type, String dataId) {
        if (events.existsByProviderAndEventKey(PROVIDER, eventKey)) return Outcome.DUPLICATE;
        if (!"payment".equals(type)) { markProcessed(eventKey); return Outcome.PROCESSED; }
        Payment payment = payments.findByMpPaymentId(dataId).orElse(null);
        if (payment == null) { markProcessed(eventKey); return Outcome.UNKNOWN_PAYMENT; }
        orderPayments.syncFromProvider(payment); // excecao aqui = 500 = provedor reenvia
        markProcessed(eventKey);
        return Outcome.PROCESSED;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markProcessed(String eventKey) {
        ProcessedWebhookEvent event = new ProcessedWebhookEvent();
        event.setProvider(PROVIDER);
        event.setEventKey(eventKey);
        try { events.saveAndFlush(event); }
        catch (DataIntegrityViolationException ignored) { /* entrega concorrente do mesmo evento */ }
    }
}

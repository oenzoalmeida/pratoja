package br.com.pratoja.pratoja.payments;

import br.com.pratoja.pratoja.domain.ProcessedWebhookEvent;
import br.com.pratoja.pratoja.domain.Payment;
import br.com.pratoja.pratoja.repository.PaymentRepository;
import br.com.pratoja.pratoja.repository.ProcessedWebhookEventRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Processamento de webhooks do Mercado Pago com deduplicacao forte:
 *  a chave (provider, event_key) unica no banco garante processamento uma unica vez. */
@Service
public class MercadoPagoWebhookService {
    private static final String PROVIDER = "mercadopago";

    private final ProcessedWebhookEventRepository events;
    private final PaymentRepository payments;
    private final PixPaymentService pixPayments;

    public MercadoPagoWebhookService(ProcessedWebhookEventRepository events, PaymentRepository payments,
                                     PixPaymentService pixPayments) {
        this.events = events;
        this.payments = payments;
        this.pixPayments = pixPayments;
    }

    public enum Outcome { PROCESSED, DUPLICATE, UNKNOWN_PAYMENT }

    /** Registra o evento uma unica vez; o processamento acontece fora (nova transacao)
     *  para que falhas de rede/API nao rolem para tras a marca de deduplicacao.
     *  Chave = id unico do evento (cada mudanca de estado do mesmo pagamento tem o seu). */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean markReceived(String eventKey) {
        if (events.existsByProviderAndEventKey(PROVIDER, eventKey)) return false;
        ProcessedWebhookEvent event = new ProcessedWebhookEvent();
        event.setProvider(PROVIDER);
        event.setEventKey(eventKey);
        try { events.saveAndFlush(event); return true; }
        catch (DataIntegrityViolationException e) { return false; }
    }

    public Outcome process(String eventKey, String type, String dataId) {
        if (!markReceived(eventKey)) return Outcome.DUPLICATE;
        if (!"payment".equals(type)) return Outcome.PROCESSED;
        Payment payment = payments.findByMpPaymentId(dataId).orElse(null);
        if (payment == null) return Outcome.UNKNOWN_PAYMENT;
        pixPayments.syncFromProvider(payment);
        return Outcome.PROCESSED;
    }
}

package br.com.pratoja.pratoja.payments;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

/** Reconciliador de pagamentos online: re-sincroniza cobrancas pendentes antigas com a API do MP.
 *  Intervalo configuravel (padrao 15 min); so faz chamadas externas quando ha pendencias. */
@Configuration
@EnableScheduling
public class PaymentReconciliationConfig {
    private final OrderPaymentService payments;

    public PaymentReconciliationConfig(OrderPaymentService payments) { this.payments = payments; }

    @Scheduled(fixedDelayString = "${pratoja.mp.reconcile-interval-ms:900000}", initialDelayString = "${pratoja.mp.reconcile-initial-delay-ms:60000}")
    public void reconcile() {
        for (var payment : payments.stalePendingPayments()) {
            try { payments.syncFromProvider(payment); } // via proxy: cada sync em transacao propria
            catch (Exception ignored) { /* proxima rodada tenta de novo; nada e confirmado sem validacao */ }
        }
    }
}

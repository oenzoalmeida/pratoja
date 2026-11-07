package br.com.pratoja.pratoja.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;

/**
 * Rate limit in-memory (janela fixa) para ações sensíveis do fluxo de pedido:
 * <ul>
 *   <li><b>Checkout</b> (POST /loja/{slug}/checkout): contém spam de pedidos falsos contra os
 *       painéis das lojas. Conta TENTATIVAS por conta (cliente autenticado).</li>
 *   <li><b>Pagamento com cartão</b> (POST .../pagamento/cartao) e <b>verificação manual</b>
 *       (POST .../pagamento/verificar): cada tentativa chama a API do Mercado Pago; o limite
 *       contém abuso que esgotaria a cota da API da loja. Conta TENTATIVAS por conta.</li>
 * </ul>
 * Ao estourar, os controllers respondem com a mesma tela e uma mensagem genérica de
 * "aguarde e tente novamente" — nada é processado e nenhuma chamada externa é feita.
 * Estado em memória da instância (restart/redeploy zera), mesma decisão dos demais limiters.
 */
@Component
public class OrderRateLimiter {

    private static final long PURGE_INTERVAL_MS = 60_000L;

    private final ConcurrentHashMap<String, Window> windows = new ConcurrentHashMap<>();
    private volatile long lastPurgeAtMs = System.currentTimeMillis();

    private final int maxCheckoutAttempts;
    private final int checkoutWindowMinutes;
    private final int maxCardAttempts;
    private final int cardWindowMinutes;
    private final int maxVerifyAttempts;
    private final int verifyWindowMinutes;

    public OrderRateLimiter(
            @Value("${pratoja.checkout.rate-limit.max-attempts:10}") int maxCheckoutAttempts,
            @Value("${pratoja.checkout.rate-limit.window-minutes:10}") int checkoutWindowMinutes,
            @Value("${pratoja.payment.card.rate-limit.max-attempts:6}") int maxCardAttempts,
            @Value("${pratoja.payment.card.rate-limit.window-minutes:10}") int cardWindowMinutes,
            @Value("${pratoja.payment.verify.rate-limit.max-attempts:12}") int maxVerifyAttempts,
            @Value("${pratoja.payment.verify.rate-limit.window-minutes:5}") int verifyWindowMinutes) {
        this.maxCheckoutAttempts = maxCheckoutAttempts;
        this.checkoutWindowMinutes = checkoutWindowMinutes;
        this.maxCardAttempts = maxCardAttempts;
        this.cardWindowMinutes = cardWindowMinutes;
        this.maxVerifyAttempts = maxVerifyAttempts;
        this.verifyWindowMinutes = verifyWindowMinutes;
    }

    /** 1 tentativa de checkout para a conta. {@code false} = janela cheia: não processe o pedido. */
    public boolean tryAcquireCheckout(String accountEmail) {
        return tryAcquire("checkout:" + accountEmail, maxCheckoutAttempts, checkoutWindowMinutes);
    }

    /** 1 tentativa de cobrança de cartão para a conta. {@code false} = não chame a API do MP. */
    public boolean tryAcquireCard(String accountEmail) {
        return tryAcquire("card:" + accountEmail, maxCardAttempts, cardWindowMinutes);
    }

    /** 1 verificação manual de pagamento para a conta. {@code false} = não chame a API do MP. */
    public boolean tryAcquireVerify(String accountEmail) {
        return tryAcquire("verify:" + accountEmail, maxVerifyAttempts, verifyWindowMinutes);
    }

    /** Apenas para testes: zera todos os buckets. */
    public void expireAllWindowsForTests() {
        windows.clear();
        lastPurgeAtMs = 0L;
    }

    private synchronized boolean tryAcquire(String key, int max, int windowMinutes) {
        long now = System.currentTimeMillis();
        purgeExpired(now);
        long windowMs = windowMinutes * 60_000L;
        Window window = windows.compute(key, (k, current) ->
                current == null || now >= current.expiresAtMs ? new Window(now + windowMs) : current);
        if (window.count >= max) return false; // janela fixa: segue bloqueado até expirar
        window.count++;
        return true;
    }

    private void purgeExpired(long now) {
        if (now - lastPurgeAtMs < PURGE_INTERVAL_MS) return;
        lastPurgeAtMs = now;
        windows.entrySet().removeIf(entry -> now >= entry.getValue().expiresAtMs);
    }

    private static final class Window {
        private final long expiresAtMs;
        private int count;

        private Window(long expiresAtMs) {
            this.expiresAtMs = expiresAtMs;
        }
    }
}

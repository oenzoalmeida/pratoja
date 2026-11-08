package br.com.pratoja.pratoja;

import br.com.pratoja.pratoja.config.OrderRateLimiter;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * OrderRateLimiter: tetos por conta em janela fixa para checkout, cartão e verificação.
 * Teste de unidade puro (sem contexto Spring): os tetos chegam pelo construtor.
 */
class OrderRateLimiterTests {

    private OrderRateLimiter newLimiter() {
        // maxCheckout=3/10min, maxCard=2/10min, maxVerify=2/5min
        return new OrderRateLimiter(3, 10, 2, 10, 2, 5);
    }

    @Test
    void checkoutBloqueiaAposTetoPorConta() {
        OrderRateLimiter limiter = newLimiter();
        assertTrue(limiter.tryAcquireCheckout("a@x.com"));
        assertTrue(limiter.tryAcquireCheckout("a@x.com"));
        assertTrue(limiter.tryAcquireCheckout("a@x.com"));
        assertFalse(limiter.tryAcquireCheckout("a@x.com"));
    }

    @Test
    void contasTemBucketsIndependentes() {
        OrderRateLimiter limiter = newLimiter();
        assertTrue(limiter.tryAcquireCheckout("a@x.com"));
        assertTrue(limiter.tryAcquireCheckout("a@x.com"));
        assertTrue(limiter.tryAcquireCheckout("a@x.com"));
        assertFalse(limiter.tryAcquireCheckout("a@x.com"));
        assertTrue(limiter.tryAcquireCheckout("b@x.com"));
    }

    @Test
    void bucketsDeAcoesSaoIndependentes() {
        OrderRateLimiter limiter = newLimiter();
        assertTrue(limiter.tryAcquireCard("a@x.com"));
        assertTrue(limiter.tryAcquireCard("a@x.com"));
        assertFalse(limiter.tryAcquireCard("a@x.com"));
        // Esgotar o cartão não afeta checkout nem verificação da mesma conta.
        assertTrue(limiter.tryAcquireCheckout("a@x.com"));
        assertTrue(limiter.tryAcquireVerify("a@x.com"));
        assertTrue(limiter.tryAcquireVerify("a@x.com"));
        assertFalse(limiter.tryAcquireVerify("a@x.com"));
    }

    @Test
    void expireAllWindowsForTestsReiniciaBuckets() {
        OrderRateLimiter limiter = newLimiter();
        assertTrue(limiter.tryAcquireCard("a@x.com"));
        assertTrue(limiter.tryAcquireCard("a@x.com"));
        assertFalse(limiter.tryAcquireCard("a@x.com"));
        limiter.expireAllWindowsForTests();
        assertTrue(limiter.tryAcquireCard("a@x.com"));
    }
}

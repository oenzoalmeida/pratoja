package br.com.pratoja.pratoja.payments;

import br.com.pratoja.pratoja.domain.StorePaymentAccount;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** Modo TEST nunca cobra com credencial de producao (e vice-versa): falha fechada. */
class StorePaymentTokenPolicyTests {

    @Test
    void testModeAcceptsTestToken() {
        assertDoesNotThrow(() -> StorePaymentService.assertTokenMatchesMode(
                StorePaymentAccount.Mode.TEST, "TEST-1234-abcd"));
    }

    @Test
    void testModeRejectsProductionToken() {
        assertThrows(IllegalStateException.class, () -> StorePaymentService.assertTokenMatchesMode(
                StorePaymentAccount.Mode.TEST, "APP_USR-1234-abcd"));
    }

    @Test
    void productionModeRejectsTestToken() {
        assertThrows(IllegalStateException.class, () -> StorePaymentService.assertTokenMatchesMode(
                StorePaymentAccount.Mode.PRODUCTION, "TEST-1234-abcd"));
    }

    @Test
    void productionModeAcceptsProductionToken() {
        assertDoesNotThrow(() -> StorePaymentService.assertTokenMatchesMode(
                StorePaymentAccount.Mode.PRODUCTION, "APP_USR-1234-abcd"));
    }

    @Test
    void unknownOrMissingTokenFailsClosed() {
        assertThrows(IllegalStateException.class, () -> StorePaymentService.assertTokenMatchesMode(
                StorePaymentAccount.Mode.TEST, "random-string"));
        assertThrows(IllegalStateException.class, () -> StorePaymentService.assertTokenMatchesMode(
                StorePaymentAccount.Mode.TEST, null));
    }
}

package br.com.pratoja.pratoja.payments;

import org.junit.jupiter.api.Test;

import java.util.Base64;

import static org.junit.jupiter.api.Assertions.*;

class PaymentSecretsServiceTests {

    private MercadoPagoProperties props(String key) {
        MercadoPagoProperties p = new MercadoPagoProperties();
        p.cryptoKey = key;
        return p;
    }

    @Test
    void roundTrip() {
        byte[] raw = new byte[32];
        for (int i = 0; i < 32; i++) raw[i] = (byte) i;
        PaymentSecretsService service = new PaymentSecretsService(props(Base64.getEncoder().encodeToString(raw)));
        assertTrue(service.available());
        String encrypted = service.encrypt("APP_USR-token-secreto");
        assertNotEquals("APP_USR-token-secreto", encrypted);
        assertEquals("APP_USR-token-secreto", service.decrypt(encrypted));
        // IV aleatorio: mesma entrada gera saidas diferentes
        assertNotEquals(encrypted, service.encrypt("APP_USR-token-secreto"));
    }

    @Test
    void failsClosedWithoutKey() {
        PaymentSecretsService service = new PaymentSecretsService(props(""));
        assertFalse(service.available());
        assertThrows(IllegalStateException.class, () -> service.encrypt("x"));
    }

    @Test
    void failsClosedWithWrongKeySize() {
        PaymentSecretsService service = new PaymentSecretsService(props(Base64.getEncoder().encodeToString(new byte[8])));
        assertFalse(service.available());
        assertThrows(IllegalStateException.class, () -> service.decrypt("AAAA"));
    }
}

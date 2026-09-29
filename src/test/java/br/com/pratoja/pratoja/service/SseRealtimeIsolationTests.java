package br.com.pratoja.pratoja.service;

import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Isolamento cross-tenant dos eventos SSE de admin: com 2 lojas e 2 STORE_ADMINs conectados,
 * um pedido na loja 1 NÃO gera evento no stream do admin da loja 2 (e o da loja 1 sim).
 * O PLATFORM_ADMIN (sem loja) recebe eventos de todas as lojas — decisão documentada no hub.
 * Estratégia: RealtimeHub testado diretamente com Emissors fake (determinístico, sem IO/async).
 */
class SseRealtimeIsolationTests {

    /** Emitter fake que apenas registra os eventos entregues. */
    static class RecordingEmitter extends SseEmitter {
        final List<String> events = new CopyOnWriteArrayList<>();
    }

    private static RealtimeHub hub() {
        return new RealtimeHub() {
            @Override protected void send(SseEmitter e, String name, Object data) {
                if (e instanceof RecordingEmitter r) r.events.add(name + ":" + data);
            }
        };
    }

    private static List<String> orderEvents(RecordingEmitter e) {
        return e.events.stream().filter(x -> x.startsWith("order:")).toList();
    }

    @Test
    void adminOfStore2DoesNotReceiveEventsFromStore1() {
        RealtimeHub hub = hub();
        RecordingEmitter admin1 = new RecordingEmitter(), admin2 = new RecordingEmitter(), platform = new RecordingEmitter();
        hub.registerAdmin(1L, admin1);
        hub.registerAdmin(2L, admin2);
        hub.registerPlatformAdmin(platform);

        // Novo pedido na loja 1
        hub.publish(100L, 1L, "RECEIVED");
        assertEquals(1, orderEvents(admin1).size(), "admin da loja 1 deve receber o evento da loja 1");
        assertTrue(orderEvents(admin1).get(0).contains("storeId=1"));
        assertEquals(0, orderEvents(admin2).size(), "admin da loja 2 NÃO pode receber evento da loja 1");
        assertEquals(1, orderEvents(platform).size(), "platform admin monitora todas as lojas");

        // Mudança de status na loja 2
        hub.publish(100L, 2L, "CONFIRMED");
        assertEquals(1, orderEvents(admin1).size(), "admin da loja 1 NÃO pode receber evento da loja 2");
        assertEquals(1, orderEvents(admin2).size(), "admin da loja 2 recebe o evento da própria loja");
        assertEquals(2, orderEvents(platform).size());
    }

    @Test
    void orderTrackingEmitterIsScopedToItsOwnOrder() {
        RealtimeHub hub = hub();
        RecordingEmitter tracking100 = new RecordingEmitter(), tracking101 = new RecordingEmitter();
        hub.registerOrder(100L, tracking100);
        hub.registerOrder(101L, tracking101);

        hub.publish(100L, 1L, "PREPARING");
        assertTrue(tracking100.events.stream().anyMatch(x -> x.startsWith("status:")));
        assertFalse(tracking101.events.stream().anyMatch(x -> x.startsWith("status:")));
    }

    @Test
    void connectedEventConfirmsScopedRegistration() {
        RealtimeHub hub = hub();
        RecordingEmitter admin1 = new RecordingEmitter();
        hub.registerAdmin(1L, admin1);
        assertTrue(admin1.events.get(0).startsWith("connected:"), "primeiro evento é o handshake connected");
    }
}

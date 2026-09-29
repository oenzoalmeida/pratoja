package br.com.pratoja.pratoja.service;

import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.*;

/**
 * Hub de eventos SSE.
 *
 * Isolamento por loja (multitenant):
 * - Cliente: subscribe(orderId) — emissões por pedido, como antes.
 * - Admin de loja: subscribeAdmin(storeId) — recebe SOMENTE eventos da própria loja.
 * - PLATFORM_ADMIN: subscribePlatformAdmin() — DECISÃO DE DESIGN: o platform admin recebe
 *   eventos de TODAS as lojas (papel de monitoria/operação da plataforma; ele não tem loja).
 *
 * publish(orderId, storeId, status) alcança apenas: os Emissors daquele pedido (tracking) e
 * os Emissors de admin daquela store_id (mais os platform admins). Admin de outra loja não recebe nada.
 */
@Service
public class RealtimeHub {
    private final Map<Long, List<SseEmitter>> orders = new ConcurrentHashMap<>();
    private final Map<Long, List<SseEmitter>> admins = new ConcurrentHashMap<>(); // storeId -> emitters
    private final List<SseEmitter> platformAdmins = new CopyOnWriteArrayList<>();

    /** Tracking do cliente: eventos de status de um pedido específico. */
    public SseEmitter subscribe(Long id) {
        SseEmitter e = new SseEmitter(30 * 60_000L);
        registerOrder(id, e);
        return e;
    }

    /** Registro de emitter fornecido externamente (usado em testes com emitters fake). */
    void registerOrder(Long id, SseEmitter e) {
        orders.computeIfAbsent(id, x -> new CopyOnWriteArrayList<>()).add(e);
        cleanup(e, () -> orders.getOrDefault(id, List.of()).remove(e));
        send(e, "connected", Map.of("orderId", id));
    }

    /** Painel do STORE_ADMIN: eventos apenas da loja informada. */
    public SseEmitter subscribeAdmin(Long storeId) {
        SseEmitter e = new SseEmitter(30 * 60_000L);
        registerAdmin(storeId, e);
        return e;
    }

    /** Painel do PLATFORM_ADMIN: eventos de todas as lojas. */
    public SseEmitter subscribePlatformAdmin() {
        SseEmitter e = new SseEmitter(30 * 60_000L);
        platformAdmins.add(e);
        cleanup(e, () -> platformAdmins.remove(e));
        send(e, "connected", Map.of("scope", "platform"));
        return e;
    }

    /** Registro de emitter fornecido externamente (usado em testes com emitters fake). */
    void registerPlatformAdmin(SseEmitter e) {
        platformAdmins.add(e);
        cleanup(e, () -> platformAdmins.remove(e));
        send(e, "connected", Map.of("scope", "platform"));
    }

    void registerAdmin(Long storeId, SseEmitter e) {
        admins.computeIfAbsent(storeId, x -> new CopyOnWriteArrayList<>()).add(e);
        cleanup(e, () -> admins.getOrDefault(storeId, List.of()).remove(e));
        send(e, "connected", Map.of("storeId", storeId));
    }

    /** Publica mudança de status: tracking do pedido + painel dos admins DAQUELA loja + platform admins. */
    public void publish(Long id, Long storeId, String status) {
        orders.getOrDefault(id, List.of()).forEach(e -> send(e, "status", Map.of("orderId", id, "status", status)));
        List<SseEmitter> targets = new ArrayList<>(admins.getOrDefault(storeId, List.of()));
        targets.addAll(platformAdmins);
        targets.forEach(e -> send(e, "order", Map.of("orderId", id, "storeId", storeId, "status", status)));
    }

    private void cleanup(SseEmitter e, Runnable r) { e.onCompletion(r); e.onTimeout(r); e.onError(x -> r.run()); }

    /** protected para permitir emissors de captura em testes. */
    protected void send(SseEmitter e, String name, Object data) {
        try { e.send(SseEmitter.event().name(name).data(data)); } catch (IOException ex) { e.complete(); }
    }
}

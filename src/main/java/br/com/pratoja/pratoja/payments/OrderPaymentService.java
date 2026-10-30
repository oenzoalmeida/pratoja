package br.com.pratoja.pratoja.payments;

import br.com.pratoja.pratoja.domain.DomainTypes;
import br.com.pratoja.pratoja.domain.Order;
import br.com.pratoja.pratoja.domain.Payment;
import br.com.pratoja.pratoja.repository.OrderRepository;
import br.com.pratoja.pratoja.repository.PaymentRepository;
import br.com.pratoja.pratoja.service.OrderService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

/** Ciclo de vida do pagamento online (Pix e cartao) via Mercado Pago.
 *  Regras centrais:
 *  - cobranca criada com o token DA LOJA (multitenant), idempotente pela referencia unica;
 *  - estado e SEMPRE conferido/validado contra a resposta da API (referencia, valor, moeda, collector);
 *  - so "approved" confirma e avanca o pedido para a cozinha;
 *  - estorno/cancelamento/expiracao cancelam pagamento e pedido (se ainda nao entregue);
 *  - reconciliador re-sincroniza pagamentos pendentes antigos (webhook perdido/falha de rede). */
@Service
public class OrderPaymentService {
    private static final int PIX_EXPIRATION_HOURS = 2;
    private static final int RECONCILE_AFTER_MINUTES = 15;

    private final OrderRepository orders;
    private final PaymentRepository payments;
    private final StorePaymentService storePayments;
    private final MercadoPagoClient client;
    private final OrderService orderService;
    private final MercadoPagoProperties props;

    public OrderPaymentService(OrderRepository orders, PaymentRepository payments,
                               StorePaymentService storePayments, MercadoPagoClient client,
                               OrderService orderService, MercadoPagoProperties props) {
        this.orders = orders;
        this.payments = payments;
        this.storePayments = storePayments;
        this.client = client;
        this.orderService = orderService;
        this.props = props;
    }

    /** Inicia a cobranca Pix de um pedido proprio ja criado (pagamento PENDING com referencia).
     *  Idempotente pela referencia unica: repeticao retorna a cobranca ja criada. */
    @Transactional
    public Payment startPix(Long orderId) {
        Order order = orders.findById(orderId).orElseThrow(SecurityException::new);
        Payment payment = pendingOnlinePayment(order, DomainTypes.PaymentMethod.PIX);
        if (payment.getMpPaymentId() != null) return payment;
        String accessToken = storePayments.accessToken(order.getStore().getId());
        MercadoPagoClient.MpPayment created = client.createPixPayment(new MercadoPagoClient.CreatePixPayment(
                accessToken,
                order.getTotal(),
                "Pedido #" + order.getId() + " - " + order.getStore().getName(),
                order.getUser().getEmail(),
                payment.getReference(),
                props.publicBaseUrl + "/webhooks/mercadopago",
                payment.getReference(),
                OffsetDateTime.now(ZoneOffset.UTC).plusHours(PIX_EXPIRATION_HOURS).toString()));
        saveRemote(payment, created);
        return payments.save(payment);
    }

    /** Cobranca de cartao: o token do cartao e criado NO NAVEGADOR do cliente (MercadoPago.js
     *  com a public key da loja) — dados de cartao nunca passam pelo nosso servidor. */
    @Transactional
    public SyncResult chargeCard(Long orderId, String cardToken, String paymentMethodId,
                                 String issuerId, int installments) {
        Order order = orders.findById(orderId).orElseThrow(SecurityException::new);
        Payment payment = pendingOnlinePayment(order, DomainTypes.PaymentMethod.CARD);
        if (payment.getMpPaymentId() != null) return syncFromProvider(payment);
        if (cardToken == null || cardToken.isBlank() || paymentMethodId == null || paymentMethodId.isBlank())
            throw new IllegalArgumentException("Dados do cartao invalidos. Tente novamente.");
        String accessToken = storePayments.accessToken(order.getStore().getId());
        MercadoPagoClient.MpPayment created = client.createCardPayment(new MercadoPagoClient.CreateCardPayment(
                accessToken,
                order.getTotal(),
                "Pedido #" + order.getId() + " - " + order.getStore().getName(),
                order.getUser().getEmail(),
                payment.getReference(),
                props.publicBaseUrl + "/webhooks/mercadopago",
                payment.getReference(),
                cardToken, paymentMethodId, issuerId, Math.max(installments, 1)));
        saveRemote(payment, created);
        payments.save(payment);
        // A resposta da criacao ja veio da API com o token da loja: mesmo caminho de validacao do webhook.
        return applyRemoteStatus(payment, created, order.getStore().getId());
    }

    private Payment pendingOnlinePayment(Order order, DomainTypes.PaymentMethod method) {
        Payment payment = order.getPayment();
        if (payment == null || payment.getStatus() != DomainTypes.PaymentStatus.PENDING
                || payment.getMethod() != method || payment.getReference() == null)
            throw new IllegalStateException("Este pedido nao tem um pagamento online pendente.");
        return payment;
    }

    private void saveRemote(Payment payment, MercadoPagoClient.MpPayment remote) {
        payment.setMpPaymentId(String.valueOf(remote.id()));
        payment.setMpStatus(remote.status());
        payment.setQrCode(remote.qrCode());
        payment.setQrCodeBase64(remote.qrCodeBase64());
        payment.setTicketUrl(remote.ticketUrl());
    }

    /** Re-consulta a API do Mercado Pago e aplica o estado real. Usada pelo webhook, pelo botao
     *  "ja paguei" e pelo reconciliador — mesmo caminho de validacao nos tres. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public SyncResult syncFromProvider(Payment payment) {
        if (payment.getMpPaymentId() == null) return SyncResult.IGNORED;
        Payment managed = payments.findById(payment.getId()).orElseThrow(SecurityException::new);
        Long storeId = managed.getOrder().getStore().getId();
        String accessToken = storePayments.accessToken(storeId);
        MercadoPagoClient.MpPayment remote = client.getPayment(accessToken, Long.parseLong(managed.getMpPaymentId()));
        return applyRemoteStatus(managed, remote, storeId);
    }

    /** Aplica um estado verificado da API. Nunca confia em dados do POST do webhook. */
    public SyncResult applyRemoteStatus(Payment payment, MercadoPagoClient.MpPayment remote, Long storeId) {
        // Integridade: referencia, valor, moeda e a CONTA (collector) precisam bater com o pedido registrado.
        if (remote.externalReference() == null || !remote.externalReference().equals(payment.getReference()))
            return SyncResult.MISMATCH;
        if (remote.transactionAmount() == null || remote.transactionAmount().compareTo(payment.getOrder().getTotal()) != 0)
            return SyncResult.MISMATCH;
        if (remote.currencyId() == null || !remote.currencyId().equalsIgnoreCase("BRL"))
            return SyncResult.MISMATCH;
        if (!String.valueOf(remote.id()).equals(payment.getMpPaymentId()))
            return SyncResult.MISMATCH;
        String expectedCollector = storePayments.accountOf(storeId).map(a -> a.getMpUserId()).orElse(null);
        if (expectedCollector != null && remote.collectorId() != null
                && !expectedCollector.equals(String.valueOf(remote.collectorId())))
            return SyncResult.MISMATCH;

        payment.setMpStatus(remote.status());
        Order order = payment.getOrder();
        switch (remote.status()) {
            case "approved" -> {
                if (payment.getStatus() == DomainTypes.PaymentStatus.CONFIRMED) return SyncResult.UNCHANGED;
                payment.setStatus(DomainTypes.PaymentStatus.CONFIRMED);
                payment.setPaidAt(LocalDateTime.now());
                payments.save(payment);
                advanceOrder(order, DomainTypes.OrderStatus.CONFIRMED);
                return SyncResult.CONFIRMED;
            }
            case "refunded", "cancelled", "charged_back", "expired" -> {
                if (payment.getStatus() == DomainTypes.PaymentStatus.CANCELLED) return SyncResult.UNCHANGED;
                payment.setStatus(DomainTypes.PaymentStatus.CANCELLED);
                payments.save(payment);
                // Estorno/cancelamento/expiracao: o pedido so e cancelado se ainda nao foi entregue.
                if (order.getStatus() != DomainTypes.OrderStatus.DELIVERED
                        && order.getStatus() != DomainTypes.OrderStatus.CANCELLED)
                    advanceOrder(order, DomainTypes.OrderStatus.CANCELLED);
                return SyncResult.CANCELLED;
            }
            default -> { payments.save(payment); return SyncResult.UNCHANGED; }
        }
    }

    /** Reconciliacao: pagamentos online pendentes ha mais de 15 min sem webhook re-consultam a API.
     *  Cobre webhook perdido, falha de rede na entrega e indisponibilidade temporaria. */
    @Transactional(readOnly = true)
    public List<Payment> stalePendingPayments() {
        return payments.findByStatusAndMpPaymentIdIsNotNullAndCreatedAtBefore(
                DomainTypes.PaymentStatus.PENDING, LocalDateTime.now().minusMinutes(RECONCILE_AFTER_MINUTES));
    }

    /** Avanca/cancela o pedido tolerando eventos fora de ordem: transicao invalida e ignorada. */
    private void advanceOrder(Order order, DomainTypes.OrderStatus next) {
        try { orderService.transitionInStore(order.getId(), order.getStore(), next, "mercadopago:webhook"); }
        catch (IllegalArgumentException ignored) { /* estado ja avancou por outro evento/acao manual */ }
    }

    public enum SyncResult { CONFIRMED, CANCELLED, UNCHANGED, IGNORED, MISMATCH }
}

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

/** Ciclo de vida do Pix via Mercado Pago: cria a cobranca (QR dinamico), sincroniza o status
 *  re-consultando a API (webhook nunca confia no payload) e aplica confirmacao/cancelamento.
 *  Validacoes sempre: external_reference == payment.reference, valor == total do pedido, moeda BRL. */
@Service
public class PixPaymentService {
    private final OrderRepository orders;
    private final PaymentRepository payments;
    private final StorePaymentService storePayments;
    private final MercadoPagoClient client;
    private final OrderService orderService;
    private final MercadoPagoProperties props;

    public PixPaymentService(OrderRepository orders, PaymentRepository payments,
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
        Payment payment = order.getPayment();
        if (payment == null || payment.getStatus() != DomainTypes.PaymentStatus.PENDING
                || payment.getMethod() != DomainTypes.PaymentMethod.PIX || payment.getReference() == null)
            throw new IllegalStateException("Este pedido nao tem um Pix pendente.");
        if (payment.getMpPaymentId() != null) return payment;
        String accessToken = storePayments.accessToken(order.getStore().getId());
        MercadoPagoClient.MpPayment created = client.createPixPayment(new MercadoPagoClient.CreatePixPayment(
                accessToken,
                order.getTotal(),
                "Pedido #" + order.getId() + " - " + order.getStore().getName(),
                order.getUser().getEmail(),
                payment.getReference(),
                props.publicBaseUrl + "/webhooks/mercadopago",
                payment.getReference()));
        payment.setMpPaymentId(String.valueOf(created.id()));
        payment.setMpStatus(created.status());
        payment.setQrCode(created.qrCode());
        payment.setQrCodeBase64(created.qrCodeBase64());
        payment.setTicketUrl(created.ticketUrl());
        return payments.save(payment);
    }

    /** Re-consulta a API do Mercado Pago e aplica o estado real. Usada pelo webhook e pelo
     *  botao "ja paguei" do cliente (mesmo caminho de validacao nos dois casos). */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public SyncResult syncFromProvider(Payment payment) {
        if (payment.getMpPaymentId() == null) return SyncResult.IGNORED;
        Payment managed = payments.findById(payment.getId()).orElseThrow(SecurityException::new);
        Order order = managed.getOrder();
        String accessToken = storePayments.accessToken(order.getStore().getId());
        MercadoPagoClient.MpPayment remote = client.getPayment(accessToken, Long.parseLong(managed.getMpPaymentId()));
        return applyRemoteStatus(managed, remote);
    }

    /** Aplica um estado verificado da API. Nunca confia em dados do POST do webhook. */
    public SyncResult applyRemoteStatus(Payment payment, MercadoPagoClient.MpPayment remote) {
        // Integridade: referencia, valor e moeda precisam bater com o pedido registrado.
        if (remote.externalReference() == null || !remote.externalReference().equals(payment.getReference()))
            return SyncResult.MISMATCH;
        if (remote.transactionAmount() == null || remote.transactionAmount().compareTo(payment.getOrder().getTotal()) != 0)
            return SyncResult.MISMATCH;
        if (remote.currencyId() == null || !remote.currencyId().equalsIgnoreCase("BRL"))
            return SyncResult.MISMATCH;
        if (!String.valueOf(remote.id()).equals(payment.getMpPaymentId()))
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
            case "refunded", "cancelled", "charged_back" -> {
                if (payment.getStatus() == DomainTypes.PaymentStatus.CANCELLED) return SyncResult.UNCHANGED;
                payment.setStatus(DomainTypes.PaymentStatus.CANCELLED);
                payments.save(payment);
                // Estorno/cancelamento: o pedido so e cancelado se ainda nao saiu para o cliente final.
                if (order.getStatus() != DomainTypes.OrderStatus.DELIVERED
                        && order.getStatus() != DomainTypes.OrderStatus.CANCELLED)
                    advanceOrder(order, DomainTypes.OrderStatus.CANCELLED);
                return SyncResult.CANCELLED;
            }
            default -> { payments.save(payment); return SyncResult.UNCHANGED; }
        }
    }

    /** Avanca/cancela o pedido tolerando webhooks fora de ordem: transicao invalida e ignorada. */
    private void advanceOrder(Order order, DomainTypes.OrderStatus next) {
        try { orderService.transitionInStore(order.getId(), order.getStore(), next, "mercadopago:webhook"); }
        catch (IllegalArgumentException ignored) { /* estado ja avancou por outro evento/acao manual */ }
    }

    public enum SyncResult { CONFIRMED, CANCELLED, UNCHANGED, IGNORED, MISMATCH }
}

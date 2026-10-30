package br.com.pratoja.pratoja.domain;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity @Table(name = "payments")
@Getter @Setter @NoArgsConstructor
public class Payment {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @OneToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "order_id", unique = true) private Order order;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20) private DomainTypes.PaymentMethod method;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 30) private DomainTypes.PaymentStatus status;
    @Column(name = "change_for", precision = 12, scale = 2) private BigDecimal changeFor;
    @Column(name = "created_at", nullable = false) private LocalDateTime createdAt = LocalDateTime.now();
    // Integracao Mercado Pago: referencia interna unica (external_reference) e estado externo do pagamento.
    @Column(length = 64) private String reference;
    @Column(name = "mp_payment_id", length = 40) private String mpPaymentId;
    @Column(name = "mp_status", length = 30) private String mpStatus;
    @Column(name = "qr_code", columnDefinition = "TEXT") private String qrCode;
    @Column(name = "qr_code_base64", columnDefinition = "TEXT") private String qrCodeBase64;
    @Column(name = "ticket_url", length = 500) private String ticketUrl;
    @Column(name = "paid_at") private LocalDateTime paidAt;
}

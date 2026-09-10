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
}

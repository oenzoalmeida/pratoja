package br.com.pratoja.pratoja.domain;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity @Table(name = "order_status_history")
@Getter @Setter @NoArgsConstructor
public class OrderStatusHistory {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "order_id") private Order order;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 30) private DomainTypes.OrderStatus status;
    @Column(name = "changed_at", nullable = false) private LocalDateTime changedAt = LocalDateTime.now();
    @Column(name = "changed_by", nullable = false, length = 180) private String changedBy;
}

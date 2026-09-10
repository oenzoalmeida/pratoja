package br.com.pratoja.pratoja.domain;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.util.*;

@Entity @Table(name = "order_items")
@Getter @Setter @NoArgsConstructor
public class OrderItem {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "order_id") private Order order;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "product_id") private Product product;
    @Column(name = "product_name", nullable = false, length = 120) private String productName;
    @Column(name = "unit_price", nullable = false, precision = 12, scale = 2) private BigDecimal unitPrice;
    @Column(nullable = false) private Integer quantity;
    @Column(name = "line_total", nullable = false, precision = 12, scale = 2) private BigDecimal lineTotal;
    @Column(length = 400) private String notes;
    @OneToMany(mappedBy = "orderItem", cascade = CascadeType.ALL, orphanRemoval = true) private List<OrderItemOption> options = new ArrayList<>();
}

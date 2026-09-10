package br.com.pratoja.pratoja.domain;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

@Entity @Table(name = "orders")
@Getter @Setter @NoArgsConstructor
public class Order {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Version private Long version;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "user_id") private User user;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "address_id") private Address address;
    @Enumerated(EnumType.STRING) @Column(name = "fulfillment_type", nullable = false, length = 20) private DomainTypes.FulfillmentType fulfillmentType;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 30) private DomainTypes.OrderStatus status;
    @Column(nullable = false, precision = 12, scale = 2) private BigDecimal subtotal;
    @Column(name = "delivery_fee", nullable = false, precision = 12, scale = 2) private BigDecimal deliveryFee;
    @Column(nullable = false, precision = 12, scale = 2) private BigDecimal total;
    @Column(length = 600) private String notes;
    @Column(name = "created_at", nullable = false) private LocalDateTime createdAt = LocalDateTime.now();
    @Column(name = "updated_at", nullable = false) private LocalDateTime updatedAt = LocalDateTime.now();
    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true) @OrderBy("id") private List<OrderItem> items = new ArrayList<>();
    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true) @OrderBy("changedAt") private List<OrderStatusHistory> statusHistory = new ArrayList<>();
    @OneToOne(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true) private Payment payment;
}

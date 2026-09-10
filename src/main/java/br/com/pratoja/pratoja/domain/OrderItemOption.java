package br.com.pratoja.pratoja.domain;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;

@Entity @Table(name = "order_item_options")
@Getter @Setter @NoArgsConstructor
public class OrderItemOption {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "order_item_id") private OrderItem orderItem;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "option_id") private ProductOption option;
    @Column(name = "group_name", nullable = false, length = 80) private String groupName;
    @Column(name = "option_name", nullable = false, length = 100) private String optionName;
    @Column(name = "price_delta", nullable = false, precision = 12, scale = 2) private BigDecimal priceDelta;
}

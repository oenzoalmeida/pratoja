package br.com.pratoja.pratoja.domain;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;

@Entity @Table(name = "product_options")
@Getter @Setter @NoArgsConstructor
public class ProductOption {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "group_id") private OptionGroup group;
    @Column(nullable = false, length = 100) private String name;
    @Column(name = "price_delta", nullable = false, precision = 12, scale = 2) private BigDecimal priceDelta = BigDecimal.ZERO;
    @Column(nullable = false) private boolean available = true;
    @Column(nullable = false) private Integer sortOrder;
}

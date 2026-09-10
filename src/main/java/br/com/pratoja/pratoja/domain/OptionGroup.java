package br.com.pratoja.pratoja.domain;

import jakarta.persistence.*;
import lombok.*;

@Entity @Table(name = "option_groups")
@Getter @Setter @NoArgsConstructor
public class OptionGroup {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "product_id") private Product product;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20) private DomainTypes.OptionType type;
    @Column(nullable = false, length = 80) private String name;
    @Column(nullable = false) private Integer minSelections;
    @Column(nullable = false) private Integer maxSelections;
    @Column(nullable = false) private Integer sortOrder;
}

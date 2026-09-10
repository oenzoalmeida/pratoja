package br.com.pratoja.pratoja.domain;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;

@Entity @Table(name = "products")
@Getter @Setter @NoArgsConstructor
public class Product {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "category_id") private Category category;
    @Column(nullable = false, length = 120) private String name;
    @Column(nullable = false, length = 600) private String description;
    @Column(nullable = false, precision = 12, scale = 2) private BigDecimal price;
    @Column(name = "image_path", length = 300) private String imagePath;
    @Column(nullable = false) private boolean available = true;
    @Column(nullable = false) private boolean customizable;
    @Column(nullable = false) private boolean featured;
    @Column(nullable = false) private boolean archived;
}

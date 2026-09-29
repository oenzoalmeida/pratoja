package br.com.pratoja.pratoja.domain;

import jakarta.persistence.*;
import lombok.*;

@Entity @Table(name = "categories")
@Getter @Setter @NoArgsConstructor
public class Category {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "store_id") private Store store;
    @Column(nullable = false, length = 80) private String name;
    @Column(nullable = false) private Integer sortOrder = 0;
    @Column(nullable = false) private boolean active = true;
}

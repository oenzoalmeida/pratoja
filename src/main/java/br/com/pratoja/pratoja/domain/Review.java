package br.com.pratoja.pratoja.domain;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity @Table(name = "reviews")
@Getter @Setter @NoArgsConstructor
public class Review {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @OneToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "order_id", unique = true) private Order order;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "user_id") private User user;
    @Column(nullable = false) private Integer rating;
    @Column(length = 600) private String comment;
    @Column(name = "created_at", nullable = false) private LocalDateTime createdAt = LocalDateTime.now();
}

package br.com.pratoja.pratoja.domain;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity @Table(name = "users")
@Getter @Setter @NoArgsConstructor
public class User {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(nullable = false, length = 100) private String name;
    @Column(nullable = false, unique = true, length = 180) private String email;
    @Column(nullable = false, length = 20) private String phone;
    @Column(name = "password_hash", nullable = false) private String passwordHash;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20) private DomainTypes.Role role;
    @Column(nullable = false) private boolean active = true;
    @Column(name = "created_at", nullable = false) private LocalDateTime createdAt = LocalDateTime.now();
}

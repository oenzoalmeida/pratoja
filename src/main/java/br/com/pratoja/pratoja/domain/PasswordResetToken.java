package br.com.pratoja.pratoja.domain;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity @Table(name = "password_reset_tokens")
@Getter @Setter @NoArgsConstructor
public class PasswordResetToken {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "user_id") private User user;
    @Column(name = "token_hash", nullable = false, unique = true, length = 64) private String tokenHash;
    @Column(name = "expires_at", nullable = false) private LocalDateTime expiresAt;
    @Column(name = "used_at") private LocalDateTime usedAt;
}

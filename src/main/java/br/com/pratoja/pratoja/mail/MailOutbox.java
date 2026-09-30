package br.com.pratoja.pratoja.mail;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * Linha do outbox de e-mails transacionais. A inserção acontece na MESMA transação do
 * evento de negócio; o envio é assíncrono pelo scheduler (apenas com pratoja.mail.enabled=true).
 */
@Entity @Table(name = "mail_outbox")
@Getter @Setter @NoArgsConstructor
public class MailOutbox {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "to_email", nullable = false, length = 200) private String toEmail;
    @Column(nullable = false, length = 100) private String template;
    @Column(name = "payload_json", nullable = false, columnDefinition = "text") private String payloadJson;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 16) private MailOutboxStatus status = MailOutboxStatus.PENDING;
    @Column(nullable = false) private Integer attempts = 0;
    @Column(name = "next_attempt_at") private LocalDateTime nextAttemptAt;
    @Column(name = "last_error", length = 500) private String lastError;
    @Column(name = "created_at", nullable = false) private LocalDateTime createdAt = LocalDateTime.now();
    @Column(name = "sent_at") private LocalDateTime sentAt;
}

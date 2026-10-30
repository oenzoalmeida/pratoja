package br.com.pratoja.pratoja.domain;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

/** Idempotencia de webhooks: chave unica (provider, event_key) garante processamento uma unica vez,
 *  mesmo com reenvios do provedor ou chegada fora de ordem. */
@Entity @Table(name = "processed_webhook_events")
@Getter @Setter @NoArgsConstructor
public class ProcessedWebhookEvent {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(nullable = false, length = 20) private String provider;
    @Column(name = "event_key", nullable = false, length = 160) private String eventKey;
    @Column(name = "received_at", nullable = false) private LocalDateTime receivedAt = LocalDateTime.now();
}

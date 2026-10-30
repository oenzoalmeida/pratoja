package br.com.pratoja.pratoja.repository;

import br.com.pratoja.pratoja.domain.ProcessedWebhookEvent;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProcessedWebhookEventRepository extends JpaRepository<ProcessedWebhookEvent, Long> {
    boolean existsByProviderAndEventKey(String provider, String eventKey);
}

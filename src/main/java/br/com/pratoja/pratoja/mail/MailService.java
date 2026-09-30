package br.com.pratoja.pratoja.mail;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Outbox de e-mails transacionais.
 * - {@link #enqueue}: grava a linha na MESMA transação do evento de negócio (Propagation.REQUIRED).
 * - {@link #processDue}: chamado pelo scheduler (fixDelay ~30s, apenas com pratoja.mail.enabled=true);
 *   envia e marca SENT/FAILED com retry exponencial (~1min, 5min, 15min) e DEAD após 5 tentativas.
 * Com pratoja.mail.enabled=false nenhum e-mail real é enviado e o enqueue é no-op.
 */
@Service @RequiredArgsConstructor
@Slf4j
public class MailService {
    static final int MAX_ATTEMPTS = 5;
    private static final int BATCH_SIZE = 20;
    /** Backoff exponencial aproximado por tentativa acumulada: 1min, 5min, 15min, 15min. */
    private static final Duration[] BACKOFF = {
            Duration.ofMinutes(1), Duration.ofMinutes(5), Duration.ofMinutes(15), Duration.ofMinutes(15)
    };

    private final MailOutboxRepository outbox;
    private final ObjectProvider<MailPort> mailPort;
    private final TransactionTemplate transactions;
    private final ObjectMapper objectMapper;

    @Value("${pratoja.mail.enabled:false}") private boolean enabled;

    /** Grava o e-mail no outbox dentro da transação corrente (no-op quando o mail está desligado). */
    @Transactional
    public void enqueue(String to, String template, Map<String, Object> vars) {
        if (!enabled) {
            log.debug("pratoja.mail.enabled=false: e-mail '{}' para {} não foi enfileirado", template, to);
            return;
        }
        MailOutbox mail = new MailOutbox();
        mail.setToEmail(to);
        mail.setTemplate(template);
        mail.setPayloadJson(objectMapper.writeValueAsString(vars));
        mail.setStatus(MailOutboxStatus.PENDING);
        mail.setAttempts(0);
        mail.setNextAttemptAt(LocalDateTime.now());
        mail.setCreatedAt(LocalDateTime.now());
        outbox.save(mail);
    }

    /** Processa um lote de linhas vencidas. Cada linha tem transação própria (falha isolada). */
    public void processDue() {
        if (!enabled) return;
        List<Long> dueIds = outbox.findDueIds(LocalDateTime.now(), BATCH_SIZE);
        for (Long id : dueIds) {
            try {
                processOne(id);
            } catch (Exception e) {
                log.error("Falha ao processar outbox id={}", id, e);
            }
        }
    }

    void processOne(Long id) {
        transactions.executeWithoutResult(status -> {
            MailOutbox mail = outbox.findById(id).orElse(null);
            if (mail == null || mail.getStatus() == MailOutboxStatus.SENT || mail.getStatus() == MailOutboxStatus.DEAD) return;
            if (mail.getNextAttemptAt() != null && mail.getNextAttemptAt().isAfter(LocalDateTime.now())) return;
            MailPort port = mailPort.getIfAvailable();
            if (port == null) {
                fail(mail, "MailPort indisponível: pratoja.mail.enabled=true sem SMTP configurado");
                return;
            }
            try {
                Map<String, Object> vars = objectMapper.readValue(mail.getPayloadJson(), Map.class);
                port.send(mail.getToEmail(), mail.getTemplate(), vars);
                mail.setStatus(MailOutboxStatus.SENT);
                mail.setSentAt(LocalDateTime.now());
                mail.setLastError(null);
                log.info("E-mail outbox #{} ('{}' para {}) enviado", mail.getId(), mail.getTemplate(), mail.getToEmail());
            } catch (Exception e) {
                fail(mail, e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage());
            }
        });
    }

    private void fail(MailOutbox mail, String error) {
        mail.setAttempts(mail.getAttempts() + 1);
        String message = error == null ? "erro desconhecido" : error;
        mail.setLastError(message.length() > 500 ? message.substring(0, 500) : message);
        if (mail.getAttempts() >= MAX_ATTEMPTS) {
            mail.setStatus(MailOutboxStatus.DEAD);
            log.error("E-mail outbox #{} para {} marcado como DEAD após {} tentativas: {}",
                    mail.getId(), mail.getToEmail(), mail.getAttempts(), mail.getLastError());
        } else {
            mail.setStatus(MailOutboxStatus.FAILED);
            mail.setNextAttemptAt(LocalDateTime.now().plus(BACKOFF[Math.min(mail.getAttempts(), BACKOFF.length) - 1]));
            log.warn("E-mail outbox #{} para {} falhou (tentativa {}); retry em {}: {}",
                    mail.getId(), mail.getToEmail(), mail.getAttempts(), mail.getNextAttemptAt(), mail.getLastError());
        }
    }
}

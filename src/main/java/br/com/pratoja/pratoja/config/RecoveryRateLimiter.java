package br.com.pratoja.pratoja.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Rate limit in-memory (janela fixa simples) para os endpoints públicos de recuperação de senha.
 *
 * Decisões de segurança (anti-enumeração e anti-flood):
 * <ul>
 *   <li><b>Conta TENTATIVAS</b>, não apenas envios/enqueues — cada POST consome 1 crédito do bucket.</li>
 *   <li><b>Aplica-se SEMPRE</b>, inclusive com {@code pratoja.mail.enabled=false} (o endpoint existe
 *       sempre; com o mail desligado o custo restante é a geração de token no banco — ainda assim limitado).</li>
 *   <li><b>Nunca altera a resposta HTTP</b>: ao estourar, o controller devolve a MESMA view/mensagem
 *       genérica de sucesso (200) e apenas NÃO processa/enfileira nada (log warn). Responder 429 criaria
 *       um oráculo que distingue "limite estourado" de "e-mail inexistente".</li>
 *   <li>Duas dimensões de limitação: <b>por IP</b> (ex.: 5 tentativas/10min, buckets separados para
 *       /recuperar-senha e /redefinir-senha) e <b>por e-mail</b> (ex.: 3 tentativas/hora), este último
 *       imune a spoofing de IP e responsável por conter o flood de outbox quando o mail estiver ligado.</li>
 * </ul>
 *
 * O estado é volátil (memória da instância): restart/redeploy zera os contadores e o limiter não compartilha
 * estado entre réplicas. Aceitável para proteção contra flood/enumeração; não é uma defesa de infraestrutura.
 */
@Component
public class RecoveryRateLimiter {

    /** Purga amortizada dos buckets expirados (no máximo 1x por intervalo) para conter crescimento do mapa. */
    private static final long PURGE_INTERVAL_MS = 60_000L;

    private final ConcurrentHashMap<String, Window> windows = new ConcurrentHashMap<>();
    private volatile long lastPurgeAtMs = System.currentTimeMillis();

    /** Máximo de tentativas por IP por janela nos endpoints de recuperação/redefinição. */
    @Value("${pratoja.recovery.rate-limit.max-attempts-per-ip:5}") private int maxAttemptsPerIp;
    /** Janela fixa (minutos) dos buckets por IP. */
    @Value("${pratoja.recovery.rate-limit.window-minutes:10}") private int ipWindowMinutes;
    /** Máximo de tentativas por e-mail por janela (limita tokens gerados/enqueued por conta). */
    @Value("${pratoja.recovery.rate-limit.max-per-email:3}") private int maxPerEmail;
    /** Janela fixa (minutos) do bucket por e-mail. */
    @Value("${pratoja.recovery.rate-limit.email-window-minutes:60}") private int emailWindowMinutes;

    /** 1 tentativa de POST /recuperar-senha para o IP. {@code false} = janela cheia: não processe. */
    public boolean tryAcquireRecoveryIp(String ip) {
        return tryAcquire("rec-ip:" + ip, maxAttemptsPerIp, ipWindowMinutes);
    }

    /** 1 tentativa de POST /redefinir-senha para o IP (bucket separado do de recuperação). */
    public boolean tryAcquireResetIp(String ip) {
        return tryAcquire("reset-ip:" + ip, maxAttemptsPerIp, ipWindowMinutes);
    }

    /**
     * 1 tentativa de recuperação para o e-mail informado (normalizado, case-insensitive).
     * Conta qualquer tentativa (e-mail existente ou não — a resposta é genérica de qualquer forma),
     * o que impede que IPs limitados "reservem" crédito em buckets de terceiros.
     */
    public boolean tryAcquireEmail(String email) {
        String normalized = email == null ? "" : email.strip().toLowerCase(Locale.ROOT);
        return tryAcquire("email:" + normalized, maxPerEmail, emailWindowMinutes);
    }

    /**
     * Apenas para testes: simula a expiração de TODAS as janelas (todos os buckets recomeçam vazios).
     * Equivalente ao tempo avançar além da janela fixa.
     */
    public void expireAllWindowsForTests() {
        windows.clear();
        lastPurgeAtMs = 0L;
    }

    private synchronized boolean tryAcquire(String key, int max, int windowMinutes) {
        long now = System.currentTimeMillis();
        purgeExpired(now);
        long windowMs = windowMinutes * 60_000L;
        Window window = windows.compute(key, (k, current) ->
                current == null || now >= current.expiresAtMs ? new Window(now + windowMs) : current);
        if (window.count >= max) return false; // janela cheia: segue bloqueado até expirar (janela fixa)
        window.count++;
        return true;
    }

    private void purgeExpired(long now) {
        if (now - lastPurgeAtMs < PURGE_INTERVAL_MS) return;
        lastPurgeAtMs = now;
        windows.entrySet().removeIf(entry -> now >= entry.getValue().expiresAtMs);
    }

    /** Janela fixa: contador e instante de expiração. Mutações só acontecem sob o lock de {@link #tryAcquire}. */
    private static final class Window {
        private final long expiresAtMs;
        private int count;

        private Window(long expiresAtMs) {
            this.expiresAtMs = expiresAtMs;
        }
    }
}

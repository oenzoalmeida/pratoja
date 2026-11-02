package br.com.pratoja.pratoja.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Rate limit in-memory (janela fixa) para os endpoints públicos de autenticação:
 * POST /login e POST /cadastro. Complementa o {@link RecoveryRateLimiter} (recuperação de senha).
 *
 * Decisões de segurança (anti-força-bruta sem bloquear usuários legítimos):
 * <ul>
 *   <li><b>Login conta FALHAS</b>, não tentativas: os buckets por IP/e-mail só são incrementados
 *       quando a autenticação falha. No sucesso, zera-se APENAS o bucket do e-mail autenticado
 *       (o legítimo desbloqueia a própria conta); o bucket por IP nunca é zerado por sucesso,
 *       senão um atacante com uma conta válida limparia falhas feitas contra outras contas.
 *       Um usuário legítimo atrás de NAT que erra a senha algumas vezes e acerta não fica
 *       bloqueado: o teto por IP é folgado e a janela expira sozinha.</li>
 *   <li>Duas dimensões no login: <b>por IP</b> (contém spray a partir de uma origem) e <b>por e-mail</b>
 *       (contém credential stuffing contra uma conta específica, imune a rotação de IP).</li>
 *   <li>Quando bloqueado, o filtro responde ANTES da autenticação com redirect para
 *       {@code /login?limite} (mensagem "aguarde e tente novamente"). Isso não cria oráculo de
 *       enumeração: a resposta não distingue conta existente de inexistente.</li>
 *   <li><b>Cadastro conta TENTATIVAS por IP</b> (janela de 1h): contém criação em massa de contas.
 *       O teto default é generoso para tolerar laboratórios/NAT; erros de validação também consomem,
 *       então o controller responde a mesma view de cadastro com mensagem genérica de limite.</li>
 * </ul>
 *
 * O estado é volátil (memória da instância): restart/redeploy zera os contadores e o limiter não
 * compartilha estado entre réplicas. Aceitável para conter flood/força bruta oportunista; não é uma
 * defesa de infraestrutura contra botnets distribuídas.
 */
@Component
public class AuthRateLimiter {

    /** Purga amortizada dos buckets expirados (no máximo 1x por intervalo) para conter crescimento do mapa. */
    private static final long PURGE_INTERVAL_MS = 60_000L;

    private final ConcurrentHashMap<String, Window> windows = new ConcurrentHashMap<>();
    private volatile long lastPurgeAtMs = System.currentTimeMillis();

    /** Máximo de FALHAS de login por IP por janela antes de bloquear novas tentativas. */
    @Value("${pratoja.login.rate-limit.max-failures-per-ip:20}") private int maxLoginFailuresPerIp;
    /** Janela fixa (minutos) do bucket de falhas por IP. */
    @Value("${pratoja.login.rate-limit.window-minutes:10}") private int loginIpWindowMinutes;
    /** Máximo de FALHAS de login por e-mail por janela (credential stuffing por conta). */
    @Value("${pratoja.login.rate-limit.max-failures-per-email:8}") private int maxLoginFailuresPerEmail;
    /** Janela fixa (minutos) do bucket de falhas por e-mail. */
    @Value("${pratoja.login.rate-limit.email-window-minutes:30}") private int loginEmailWindowMinutes;
    /** Máximo de TENTATIVAS de cadastro por IP por janela. */
    @Value("${pratoja.signup.rate-limit.max-attempts-per-ip:50}") private int maxSignupAttemptsPerIp;
    /** Janela fixa (minutos) do bucket de cadastro por IP. */
    @Value("${pratoja.signup.rate-limit.window-minutes:60}") private int signupWindowMinutes;

    /** Registra uma falha de login para o IP e o e-mail (normalizado) informados. */
    public void recordLoginFailure(String ip, String email) {
        increment("login-ip:" + ip, loginIpWindowMinutes);
        increment("login-email:" + normalize(email), loginEmailWindowMinutes);
    }

    /**
     * Zera APENAS o bucket por e-mail após um sucesso: o usuário legítimo desbloqueia a própria
     * conta. O bucket por IP NÃO é zerado — se fosse, um atacante com uma conta válida poderia
     * alternar logins bem-sucedidos para limpar as falhas acumuladas contra outras contas no
     * mesmo IP e contornar o teto. Falhas por IP expiram só com o fim da janela.
     */
    public void recordLoginSuccess(String ip, String email) {
        windows.remove("login-email:" + normalize(email));
    }

    /** {@code true} se o IP ou o e-mail já atingiu o teto de falhas na janela corrente (não consome). */
    public boolean isLoginBlocked(String ip, String email) {
        return isAtLimit("login-ip:" + ip, maxLoginFailuresPerIp)
                || isAtLimit("login-email:" + normalize(email), maxLoginFailuresPerEmail);
    }

    /** 1 tentativa de POST /cadastro para o IP. {@code false} = janela cheia: não processe. */
    public boolean tryAcquireSignupIp(String ip) {
        return tryAcquire("signup-ip:" + ip, maxSignupAttemptsPerIp, signupWindowMinutes);
    }

    /** Apenas para testes: simula a expiração de TODAS as janelas (todos os buckets recomeçam vazios). */
    public void expireAllWindowsForTests() {
        windows.clear();
        lastPurgeAtMs = 0L;
    }

    private static String normalize(String email) {
        return email == null ? "" : email.strip().toLowerCase(Locale.ROOT);
    }

    private synchronized void increment(String key, int windowMinutes) {
        long now = System.currentTimeMillis();
        purgeExpired(now);
        long windowMs = windowMinutes * 60_000L;
        Window window = windows.compute(key, (k, current) ->
                current == null || now >= current.expiresAtMs ? new Window(now + windowMs) : current);
        window.count++;
    }

    private synchronized boolean isAtLimit(String key, int max) {
        long now = System.currentTimeMillis();
        purgeExpired(now);
        Window window = windows.get(key);
        return window != null && now < window.expiresAtMs && window.count >= max;
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

    /** Janela fixa: contador e instante de expiração. Mutações só acontecem sob lock dos métodos. */
    private static final class Window {
        private final long expiresAtMs;
        private int count;

        private Window(long expiresAtMs) {
            this.expiresAtMs = expiresAtMs;
        }
    }
}iter.

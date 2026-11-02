package br.com.pratoja.pratoja.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Rate limit do POST /login (ver {@link AuthRateLimiter}). Roda ANTES do
 * {@code UsernamePasswordAuthenticationFilter} e em dois momentos:
 * <ul>
 *   <li><b>Antes</b>: se o IP ou o e-mail já acumulou falhas demais na janela, responde
 *       {@code 302 /login?limite} sem deixar a tentativa consultar banco ou comparar hash.</li>
 *   <li><b>Depois</b>: registra o resultado real da tentativa — redirect para {@code /login?error}
 *       conta como falha; qualquer outro redirect com principal autenticado conta como sucesso e
 *       zera os buckets (usuário legítimo nunca herda falhas antigas).</li>
 * </ul>
 * Registrar o resultado observando o redirect da própria cadeia (em vez de eventos de
 * autenticação) não depende de wiring de {@code AuthenticationEventPublisher} e cobre também
 * falhas por conta desativada. A resposta {@code ?limite} é um redirect igual ao de erro: não
 * cria oráculo sobre a existência da conta.
 */
@RequiredArgsConstructor @Slf4j
public class LoginRateLimitFilter extends OncePerRequestFilter {

    private final AuthRateLimiter rateLimiter;
    private final ClientIpResolver ipResolver;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        if (!"POST".equalsIgnoreCase(request.getMethod())) return true;
        // requestURI - contextPath: getServletPath() é "" sob MockMvc e varia com o mapeamento do servlet.
        String path = request.getRequestURI().substring(request.getContextPath().length());
        return !"/login".equals(path);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String ip = ipResolver.resolve(request);
        String email = request.getParameter("email");
        if (rateLimiter.isLoginBlocked(ip, email)) {
            log.warn("Rate limit de login: tentativa descartada antes da autenticação (ip={})", ip);
            response.sendRedirect(request.getContextPath() + "/login?limite");
            return;
        }
        chain.doFilter(request, response);
        String location = response.getHeader("Location");
        if (location != null && location.endsWith("/login?error")) {
            rateLimiter.recordLoginFailure(ip, email);
        } else if (request.getUserPrincipal() != null) {
            rateLimiter.recordLoginSuccess(ip, email);
        }
    }
}

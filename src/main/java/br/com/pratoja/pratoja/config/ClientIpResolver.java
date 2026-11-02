package br.com.pratoja.pratoja.config;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;

/**
 * Resolve o IP do cliente para o rate limit da recuperação de senha.
 *
 * Modelo de confiança (1 hop de proxy — caso do Render):
 * <ul>
 *   <li>O proxy do Render encaminha o IP real em {@code X-Forwarded-For}. Por convenção, cada proxy
 *       <b>anexa</b> ao header o IP de quem conectou nele. Com exatamente 1 hop confiável, a entrada
 *       mais à <b>DIREITA</b> é o IP real do cliente (adicionado pelo proxy); as entradas à esquerda
 *       chegam do cliente e são <b>spoofáveis</b> — por isso são ignoradas.</li>
 *   <li>Usar a entrada mais à ESQUERDA (é o que o {@code ForwardedHeaderFilter} /
 *       {@code server.forward-headers-strategy=framework} faz) permitiria que um atacante trocasse o
 *       header a cada request e criasse buckets ilimitados, furando o limite por IP. Por isso o limite
 *       NÃO depende de {@code RequestContextHolder} nem de strategy de framework.</li>
 *   <li>Sem o header (acesso direto / ambiente local), usa {@link HttpServletRequest#getRemoteAddr()}.</li>
 * </ul>
 */
@Component
public class ClientIpResolver {

    /**
     * Quantos proxies confiáveis separam a aplicação do cliente (default 1 — ex.: proxy reverso
     * na frente do container). Com 0, o X-Forwarded-For é IGNORADO (identidade = remoteAddr,
     * não-spoofável): use 0 quando NÃO houver proxy, senão um cliente pode forjar o header e
     * criar buckets de rate limit ilimitados. Deve corresponder à topologia real do deploy.
     */
    @org.springframework.beans.factory.annotation.Value("${pratoja.security.trusted-proxy-hops:1}")
    private int trustedProxyHops;

    public String resolve(HttpServletRequest request) {
        String forwarded = trustedProxyHops > 0 ? request.getHeader("X-Forwarded-For") : null;
        if (forwarded != null && !forwarded.isBlank()) {
            int lastComma = forwarded.lastIndexOf(',');
            String rightmost = (lastComma >= 0 ? forwarded.substring(lastComma + 1) : forwarded).trim();
            if (!rightmost.isEmpty()) return rightmost;
        }
        String remoteAddr = request.getRemoteAddr();
        return remoteAddr == null || remoteAddr.isBlank() ? "desconhecido" : remoteAddr;
    }
}

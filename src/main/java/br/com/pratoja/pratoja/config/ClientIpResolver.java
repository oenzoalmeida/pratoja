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

    public String resolve(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            int lastComma = forwarded.lastIndexOf(',');
            String rightmost = (lastComma >= 0 ? forwarded.substring(lastComma + 1) : forwarded).trim();
            if (!rightmost.isEmpty()) return rightmost;
        }
        String remoteAddr = request.getRemoteAddr();
        return remoteAddr == null || remoteAddr.isBlank() ? "desconhecido" : remoteAddr;
    }
}

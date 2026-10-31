package br.com.pratoja.pratoja.config;

import br.com.pratoja.pratoja.repository.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * Guarda de troca obrigatória de senha e de conta desativada.
 * - Conta com mustChangePassword=true só acessa /trocar-senha e /logout (excluídos no registro).
 * - Conta desativada (active=false) tem a sessão encerrada na hora e volta para /login:
 *   é o mecanismo de revogação de sessão de admins aposentados (o app usa sessões Tomcat
 *   em memória, sem registro JDBC, então a verificação por request é o ponto de corte).
 */
@Component
public class MustChangePasswordInterceptor implements HandlerInterceptor {
    private final UserRepository users;

    public MustChangePasswordInterceptor(UserRepository users) { this.users = users; }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || auth instanceof AnonymousAuthenticationToken) return true;
        var user = users.findByEmailIgnoreCase(auth.getName()).orElse(null);
        if (user == null) return true; // principal sem conta persistida (ex.: mock): mantém comportamento anterior
        if (!user.isActive()) {
            var session = request.getSession(false);
            if (session != null) session.invalidate();
            SecurityContextHolder.clearContext();
            response.sendRedirect("/login");
            return false;
        }
        if (user.isMustChangePassword()) {
            response.sendRedirect("/trocar-senha");
            return false;
        }
        return true;
    }
}

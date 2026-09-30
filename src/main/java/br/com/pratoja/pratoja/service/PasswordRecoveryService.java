package br.com.pratoja.pratoja.service;

import br.com.pratoja.pratoja.domain.User;
import br.com.pratoja.pratoja.mail.MailService;
import br.com.pratoja.pratoja.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Fluxo real de recuperação de senha (usado apenas quando pratoja.mail.enabled=true).
 * A criação do PasswordResetToken e a gravação do e-mail no outbox acontecem na MESMA
 * transação. A resposta para o usuário é sempre genérica (definida no controller) para
 * não revelar a existência de contas; o link NUNCA é exposto em tela neste modo.
 */
@Service @RequiredArgsConstructor
public class PasswordRecoveryService {
    static final int TOKEN_EXPIRES_MINUTES = 30;

    private final AccountService accounts;
    private final UserRepository users;
    private final MailService mail;

    @Value("${pratoja.mail.base-url:http://localhost:8080}") private String baseUrl;

    /**
     * @return true quando o e-mail corresponde a uma conta existente (uso interno/testes;
     *         nunca refletido na resposta HTTP).
     */
    @Transactional
    public boolean startReset(String email) {
        String raw = accounts.requestReset(email);
        if (raw == null) return false;
        Optional<User> user = users.findByEmailIgnoreCase(email == null ? "" : email);
        if (user.isEmpty()) return false;
        mail.enqueue(user.get().getEmail(), "password-reset", vars(raw));
        return true;
    }

    Map<String, Object> vars(String rawToken) {
        Map<String, Object> vars = new LinkedHashMap<>();
        vars.put("subject", "Redefinição de senha — PratoJá");
        vars.put("resetUrl", baseUrl.replaceAll("/$", "") + "/redefinir-senha?token=" + rawToken);
        vars.put("expiresMinutes", TOKEN_EXPIRES_MINUTES);
        return vars;
    }
}

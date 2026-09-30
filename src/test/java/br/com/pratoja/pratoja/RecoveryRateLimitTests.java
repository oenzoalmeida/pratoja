package br.com.pratoja.pratoja;

import br.com.pratoja.pratoja.config.RecoveryRateLimiter;
import br.com.pratoja.pratoja.repository.PasswordResetTokenRepository;
import br.com.pratoja.pratoja.repository.UserRepository;
import br.com.pratoja.pratoja.service.AccountService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Rate limit da recuperação de senha (POST /recuperar-senha e POST /redefinir-senha).
 *
 * Cenário: mail DESLIGADO (default) e demo-mode DESLIGADO — ou seja, todo POST de /recuperar-senha
 * para conta existente gera um PasswordResetToken real (sem demoLink em tela), permitindo provar via
 * contagem de tokens que a tentativa limitada não processa nada.
 *
 * O limite por E-MAIL fica alto aqui para isolar a dimensão sob teste (bucket por IP); a dimensão
 * por e-mail é coberta por RecoveryRateLimitMailEnabledTests.
 *
 * Anti-enumeração: a resposta quando limitada deve ser 200 e IDÊNTICA à resposta de e-mail inexistente
 * (comparação ignora apenas o valor do CSRF, que muda a cada request).
 */
@SpringBootTest(properties = {
        "pratoja.demo-mode=false",
        "pratoja.recovery.rate-limit.max-attempts-per-ip=5",
        "pratoja.recovery.rate-limit.window-minutes=10",
        "pratoja.recovery.rate-limit.max-per-email=100",
        "pratoja.recovery.rate-limit.email-window-minutes=60"
})
@AutoConfigureMockMvc
class RecoveryRateLimitTests {
    private static final String NEUTRAL_MESSAGE = "Se o e-mail estiver cadastrado";
    private static final String LINK_MARKER = "redefinir-senha?token=";
    private static final String INVALID_LINK_MESSAGE = "Link inválido ou expirado.";
    private static final String SENSIBLE_PASSWORD = "SenhaForte123";

    @Autowired MockMvc mvc;
    @Autowired UserRepository users;
    @Autowired PasswordResetTokenRepository tokens;
    @Autowired AccountService accounts;
    @Autowired RecoveryRateLimiter limiter;

    @BeforeEach
    void resetLimiterState() {
        limiter.expireAllWindowsForTests();
    }

    private void registerUser(String email) throws Exception {
        mvc.perform(post("/cadastro").with(csrf())
                .param("name", "Cliente Rate Limit")
                .param("email", email)
                .param("phone", "(11) 98888-4000")
                .param("password", SENSIBLE_PASSWORD)
                .param("confirmPassword", SENSIBLE_PASSWORD))
            .andExpect(status().isOk());
    }

    private long tokenCount(String email) {
        var user = users.findByEmailIgnoreCase(email).orElseThrow();
        return tokens.findAll().stream().filter(t -> t.getUser().getId().equals(user.getId())).count();
    }

    private MockHttpServletRequestBuilder recover(String email, String ip) {
        return post("/recuperar-senha").with(csrf()).param("email", email)
            .with(request -> { request.setRemoteAddr(ip); return request; });
    }

    private MockHttpServletRequestBuilder recoverViaProxy(String email, String proxyIp, String forwardedFor) {
        return post("/recuperar-senha").with(csrf()).param("email", email)
            .with(request -> { request.setRemoteAddr(proxyIp); request.addHeader("X-Forwarded-For", forwardedFor); return request; });
    }

    private MockHttpServletRequestBuilder resetPassword(String ip, String token, String password) {
        return post("/redefinir-senha").with(csrf())
            .param("token", token).param("password", password).param("confirmPassword", password)
            .with(request -> { request.setRemoteAddr(ip); return request; });
    }

    /** Corpo da resposta sem o valor do CSRF (muda a cada request via CookieCsrfTokenRepository). */
    private String bodyOf(MvcResult result) throws java.io.UnsupportedEncodingException {
        return result.getResponse().getContentAsString()
            .replaceAll("(name=\"_csrf\" value=\")[^\"]*(\")", "$1CSRF$2");
    }

    @Test
    void sixthAttemptWithinWindowKeepsGeneric200AndCreatesNoToken() throws Exception {
        String email = "rl-" + UUID.randomUUID() + "@example.com";
        registerUser(email);

        for (int i = 1; i <= 5; i++) {
            mvc.perform(recover(email, "203.0.113.10"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString(NEUTRAL_MESSAGE)));
        }
        assertEquals(5, tokenCount(email), "as 5 tentativas dentro do limite devem gerar tokens");

        mvc.perform(recover(email, "203.0.113.10"))
            .andExpect(status().isOk()) // 200: nada na resposta revela que o limite agiu
            .andExpect(content().string(containsString(NEUTRAL_MESSAGE)))
            .andExpect(content().string(not(containsString(LINK_MARKER))));

        assertEquals(5, tokenCount(email), "a 6ª tentativa dentro da janela não pode gerar token novo");
    }

    @Test
    void blockedResponseIsByteIdenticalToUnknownEmailResponse() throws Exception {
        String email = "rl-eq-" + UUID.randomUUID() + "@example.com";
        registerUser(email);

        for (int i = 1; i <= 5; i++) mvc.perform(recover(email, "203.0.113.11")).andExpect(status().isOk());
        MvcResult blocked = mvc.perform(recover(email, "203.0.113.11")).andExpect(status().isOk()).andReturn();

        MvcResult unknown = mvc.perform(recover("fantasma-" + UUID.randomUUID() + "@example.com", "203.0.113.12"))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString(NEUTRAL_MESSAGE)))
            .andReturn();

        assertEquals(bodyOf(unknown), bodyOf(blocked),
            "resposta limitada tem que ser indistinguível da resposta de e-mail inexistente");
    }

    @Test
    void existingAndUnknownEmailsProduceIdenticalResponses() throws Exception {
        String email = "rl-ind-" + UUID.randomUUID() + "@example.com";
        registerUser(email);

        MvcResult existing = mvc.perform(recover(email, "203.0.113.13")).andExpect(status().isOk()).andReturn();
        MvcResult unknown = mvc.perform(recover("fantasma-" + UUID.randomUUID() + "@example.com", "203.0.113.14"))
            .andExpect(status().isOk()).andReturn();

        assertEquals(bodyOf(unknown), bodyOf(existing), "existente vs inexistente não pode ter como distinguir");
        assertTrue(bodyOf(existing).contains(NEUTRAL_MESSAGE));
    }

    @Test
    void windowExpiryRestoresTheRecoveryFlow() throws Exception {
        String email = "rl-exp-" + UUID.randomUUID() + "@example.com";
        registerUser(email);

        for (int i = 1; i <= 6; i++) mvc.perform(recover(email, "203.0.113.15")).andExpect(status().isOk());
        assertEquals(5, tokenCount(email), "a 6ª tentativa (janela cheia) não gera token");

        // Simula o tempo avançar além da janela fixa: todos os buckets expiram
        limiter.expireAllWindowsForTests();

        mvc.perform(recover(email, "203.0.113.15"))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString(NEUTRAL_MESSAGE)));
        assertEquals(6, tokenCount(email), "com a janela expirada o fluxo volta a processar");
    }

    @Test
    void rightmostForwardedForIsTrustedAndSpoofedLeftEntriesIgnored() throws Exception {
        String email = "rl-xff-" + UUID.randomUUID() + "@example.com";
        registerUser(email);

        // 5 tentativas do "cliente real" 198.51.100.7 atrás do proxy 10.0.0.9, com entradas falsas à esquerda
        for (int i = 1; i <= 5; i++) {
            mvc.perform(recoverViaProxy(email, "10.0.0.9", "192.0.2.66, 198.51.100.7"))
                .andExpect(status().isOk());
        }
        assertEquals(5, tokenCount(email));

        // Mesmo IP do proxy, entradas falsas DIFERENTES à esquerda, mesmo cliente real à direita: segue bloqueado
        MvcResult blocked = mvc.perform(recoverViaProxy(email, "10.0.0.9", "9.9.9.9, 198.51.100.7"))
            .andExpect(status().isOk()).andReturn();
        assertEquals(5, tokenCount(email), "spoofar X-Forwarded-For à esquerda não pode furar o limite");

        // Cliente real diferente à direita: bucket próprio, a tentativa é processada
        mvc.perform(recoverViaProxy(email, "10.0.0.9", "192.0.2.66, 198.51.100.99"))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString(NEUTRAL_MESSAGE)));
        assertEquals(6, tokenCount(email), "outro cliente real atrás do mesmo proxy não pode herdar o bloqueio");
    }

    @Test
    void resetEndpointIsRateLimitedPerIpAndBlockedAttemptChangesNothing() throws Exception {
        String email = "rl-reset-" + UUID.randomUUID() + "@example.com";
        registerUser(email);
        String junkToken = "token-ruim-" + UUID.randomUUID();

        // 5 tentativas com token inválido esgotam o bucket do IP
        for (int i = 1; i <= 5; i++) {
            mvc.perform(resetPassword("203.0.113.20", junkToken, "QualquerSenha1"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString(INVALID_LINK_MESSAGE)));
        }

        // 6ª com token inválido (processada de verdade, vinda de outro IP) vs limitada: corpos idênticos
        MvcResult genuinelyInvalid = mvc.perform(resetPassword("203.0.113.21", junkToken, "QualquerSenha1"))
            .andExpect(status().isOk()).andReturn();
        MvcResult limited = mvc.perform(resetPassword("203.0.113.20", junkToken, "QualquerSenha1"))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString(INVALID_LINK_MESSAGE)))
            .andReturn();
        assertEquals(bodyOf(genuinelyInvalid), bodyOf(limited), "limitado tem que parecer token inválido comum");

        // 6ª tentativa com token VÁLIDO vinda do IP bloqueado: não pode consumir o token nem trocar a senha
        String rawValid = accounts.requestReset(email);
        String hashBefore = users.findByEmailIgnoreCase(email).orElseThrow().getPasswordHash();
        mvc.perform(resetPassword("203.0.113.20", rawValid, "NovaSenhaForte1"))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString(INVALID_LINK_MESSAGE)));
        assertEquals(hashBefore, users.findByEmailIgnoreCase(email).orElseThrow().getPasswordHash(),
            "tentativa limitada não pode redefinir a senha");

        // Janela expira: o mesmo token válido volta a funcionar (prova que não foi consumido)
        limiter.expireAllWindowsForTests();
        String nova = "Rotacionada" + UUID.randomUUID().toString().substring(0, 6);
        mvc.perform(resetPassword("203.0.113.20", rawValid, nova))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/login"));
        assertNotEquals(hashBefore, users.findByEmailIgnoreCase(email).orElseThrow().getPasswordHash());
    }
}

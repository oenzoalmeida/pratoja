package br.com.pratoja.pratoja;

import br.com.pratoja.pratoja.config.RecoveryRateLimiter;
import br.com.pratoja.pratoja.mail.MailOutboxRepository;
import br.com.pratoja.pratoja.repository.PasswordResetTokenRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Rate limit por E-MAIL com o subsistema de e-mail LIGADO (pratoja.mail.enabled=true, JavaMailSender
 * mockado): o limite de 3/hora por e-mail é o que impede o flood de outbox (e de e-mails reais) contra
 * uma conta específica, mesmo que o ataque venha de muitos IPs.
 *
 * O IP do teste usa limite alto (100/10min) para não interferir; o elemento sob teste é o bucket por e-mail.
 */
@SpringBootTest(properties = {
        "pratoja.mail.enabled=true",
        "spring.mail.host=localhost",
        "pratoja.mail.base-url=http://localhost:8080",
        "pratoja.mail.poll-interval-ms=3600000",
        "pratoja.recovery.rate-limit.max-attempts-per-ip=100",
        "pratoja.recovery.rate-limit.window-minutes=10",
        "pratoja.recovery.rate-limit.max-per-email=3",
        "pratoja.recovery.rate-limit.email-window-minutes=60"
})
@AutoConfigureMockMvc
class RecoveryRateLimitMailEnabledTests {
    private static final String GENERIC_SUCCESS = "Se o e-mail estiver cadastrado, enviamos as instruções";

    @Autowired MockMvc mvc;
    @Autowired MailOutboxRepository outbox;
    @Autowired PasswordResetTokenRepository tokens;
    @Autowired br.com.pratoja.pratoja.repository.UserRepository users;
    @Autowired RecoveryRateLimiter limiter;
    @MockitoBean JavaMailSender mailSender;

    @BeforeEach
    void cleanState() {
        outbox.deleteAll();
        limiter.expireAllWindowsForTests();
    }

    private void registerUser(String email) throws Exception {
        mvc.perform(post("/cadastro").with(csrf())
                .param("name", "Cliente Mail Limit")
                .param("email", email)
                .param("phone", "(11) 97777-5000")
                .param("password", "SenhaForte123")
                .param("confirmPassword", "SenhaForte123"))
            .andExpect(status().isOk());
    }

    @Test
    void fourthRecoveryAttemptForSameEmailIsNotEnqueuedAndResponseStaysGeneric() throws Exception {
        String email = "rl-mail-" + UUID.randomUUID() + "@example.com";
        registerUser(email);

        String[] bodies = new String[4];
        for (int i = 1; i <= 4; i++) {
            final String clientIp = "198.51.100.5" + i;
            MvcResult result = mvc.perform(post("/recuperar-senha").with(csrf())
                    .param("email", email)
                    .with(request -> { request.setRemoteAddr(clientIp); return request; }))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString(GENERIC_SUCCESS)))
                .andReturn();
            bodies[i - 1] = normalizedBody(result);
        }

        assertEquals(3, outbox.count(), "limite de 3 enqueues/hora por e-mail: a 4ª tentativa não enfileira");
        var userId = users.findByEmailIgnoreCase(email).orElseThrow().getId();
        assertEquals(3, tokens.findAll().stream()
            .filter(t -> t.getUser().getId().equals(userId)).count(),
            "a 4ª tentativa também não pode gerar token novo");
        assertEquals(bodies[0], bodies[3], "resposta da tentativa limitada é idêntica à de sucesso (anti-enumeração)");
        verifyNoInteractions(mailSender);
    }

    /** Corpo sem o valor do CSRF (muda a cada request via CookieCsrfTokenRepository). */
    private String normalizedBody(MvcResult result) throws java.io.UnsupportedEncodingException {
        return result.getResponse().getContentAsString()
            .replaceAll("(name=\"_csrf\" value=\")[^\"]*(\")", "$1CSRF$2");
    }

    @Test
    void emailBucketIsIndependentSoOtherAccountsKeepWorking() throws Exception {
        String victim = "rl-victim-" + UUID.randomUUID() + "@example.com";
        String other = "rl-other-" + UUID.randomUUID() + "@example.com";
        registerUser(victim);
        registerUser(other);

        for (int i = 1; i <= 3; i++) {
            mvc.perform(post("/recuperar-senha").with(csrf())
                    .param("email", victim)
                    .with(request -> { request.setRemoteAddr("203.0.113.99"); return request; }))
                .andExpect(status().isOk());
        }
        // Bucket do 'victim' estourado, mesmo IP continua atendendo outras contas
        mvc.perform(post("/recuperar-senha").with(csrf())
                .param("email", victim)
                .with(request -> { request.setRemoteAddr("203.0.113.99"); return request; }))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString(GENERIC_SUCCESS)));
        mvc.perform(post("/recuperar-senha").with(csrf())
                .param("email", other)
                .with(request -> { request.setRemoteAddr("203.0.113.99"); return request; }))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString(GENERIC_SUCCESS)));

        assertEquals(4, outbox.count(), "3 e-mails do victim + 1 do other");
        assertTrue(outbox.findAll().stream().anyMatch(m -> other.equals(m.getToEmail())),
            "outra conta no mesmo IP não pode ser bloqueada pelo bucket do victim");
    }
}

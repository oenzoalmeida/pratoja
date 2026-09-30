package br.com.pratoja.pratoja;

import br.com.pratoja.pratoja.mail.MailOutboxRepository;
import br.com.pratoja.pratoja.service.AccountService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Modo desligado (default, pratoja.mail.enabled=false): NENHUM e-mail é enviado e o
 * comportamento atual é preservado (demoLink em tela somente no demo-mode). O JavaMailSender
 * é mockado para provar que nenhuma entrega acontece, mesmo que alguém enfileire por engano.
 */
@SpringBootTest
@AutoConfigureMockMvc
class TransactionalMailDisabledTests {
    private static final String LINK_MARKER = "redefinir-senha?token=";
    private static final String DEMO_EMAIL = "cliente@pratoja.com.br";
    private static final String SEEDED_DEMO_PASSWORD = "Cliente@123";

    @Autowired MockMvc mvc;
    @Autowired MailOutboxRepository outbox;
    @Autowired AccountService accounts;
    @Autowired br.com.pratoja.pratoja.repository.PasswordResetTokenRepository tokens;
    @MockitoBean JavaMailSender mailSender;

    @BeforeEach
    void cleanOutbox() {
        outbox.deleteAll();
    }

    @Test
    void nothingIsEnqueuedOrSentWhenMailIsDisabled() throws Exception {
        mvc.perform(post("/recuperar-senha").with(csrf()).param("email", DEMO_EMAIL))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString(LINK_MARKER))) // demoLink continua em demo-mode
            .andExpect(content().string(containsString("Se o e-mail estiver cadastrado")));
        assertEquals(0, outbox.count(), "modo desligado não pode gravar no outbox");
        verifyNoInteractions(mailSender);
    }

    @Test
    void disabledModeWithDemoOffKeepsLegacyFlowWithoutLinkOrEmail() throws Exception {
        String email = "off-" + UUID.randomUUID() + "@example.com";
        mvc.perform(post("/cadastro").with(csrf())
                .param("name", "Cliente Offline")
                .param("email", email)
                .param("phone", "(11) 95555-1000")
                .param("password", "SenhaForte123")
                .param("confirmPassword", "SenhaForte123"))
            .andExpect(status().isOk());
        mvc.perform(post("/recuperar-senha").with(csrf()).param("email", email))
            .andExpect(status().isOk())
            .andExpect(content().string(not(containsString(LINK_MARKER))))
            .andExpect(content().string(containsString("Se o e-mail estiver cadastrado")));
        assertEquals(0, outbox.count());
        verifyNoInteractions(mailSender);
    }

    @Test
    void expiredTokenIsRejected() throws Exception {
        String email = "exp-" + UUID.randomUUID() + "@example.com";
        register(email);
        String raw = accounts.requestReset(email);
        var token = tokens.findByTokenHash(sha256(raw)).orElseThrow();
        token.setExpiresAt(LocalDateTime.now().minusMinutes(1));
        tokens.save(token);
        mvc.perform(post("/redefinir-senha").with(csrf())
                .param("token", raw).param("password", "ExpiradaForte1").param("confirmPassword", "ExpiradaForte1"))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("Link inválido ou expirado.")));
    }

    @Test
    void usedTokenCannotBeReused() throws Exception {
        String email = "used-" + UUID.randomUUID() + "@example.com";
        register(email);
        String raw = accounts.requestReset(email);
        mvc.perform(post("/redefinir-senha").with(csrf())
                .param("token", raw).param("password", "PrimeiraForte1").param("confirmPassword", "PrimeiraForte1"))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/login"));
        mvc.perform(post("/redefinir-senha").with(csrf())
                .param("token", raw).param("password", "SegundaForte12").param("confirmPassword", "SegundaForte12"))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("Link inválido ou expirado.")));
    }

    private void register(String email) throws Exception {
        mvc.perform(post("/cadastro").with(csrf())
                .param("name", "Cliente Expirado")
                .param("email", email)
                .param("phone", "(11) 94444-3000")
                .param("password", "SenhaForte123")
                .param("confirmPassword", "SenhaForte123"))
            .andExpect(status().isOk());
    }

    private String sha256(String value) {
        try {
            return java.util.HexFormat.of().formatHex(
                java.security.MessageDigest.getInstance("SHA-256").digest(value.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}

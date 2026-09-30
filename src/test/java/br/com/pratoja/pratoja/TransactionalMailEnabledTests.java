package br.com.pratoja.pratoja;

import br.com.pratoja.pratoja.mail.MailOutbox;
import br.com.pratoja.pratoja.mail.MailOutboxRepository;
import br.com.pratoja.pratoja.mail.MailOutboxStatus;
import br.com.pratoja.pratoja.mail.MailService;
import br.com.pratoja.pratoja.repository.PasswordResetTokenRepository;
import br.com.pratoja.pratoja.repository.UserRepository;
import br.com.pratoja.pratoja.service.PasswordRecoveryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Modo real (pratoja.mail.enabled=true): token sai por e-mail via outbox, NUNCA em tela.
 * O JavaMailSender é mockado; o scheduler não dispara durante os testes (poll-interval alto)
 * e o processamento é invocado deterministicamente via MailService.processDue().
 */
@SpringBootTest(properties = {
        "pratoja.mail.enabled=true",
        "spring.mail.host=localhost",
        "pratoja.mail.base-url=http://localhost:8080",
        "pratoja.mail.poll-interval-ms=3600000"
})
@AutoConfigureMockMvc
class TransactionalMailEnabledTests {
    private static final String LINK_MARKER = "redefinir-senha?token=";
    private static final Pattern TOKEN_IN_EMAIL = Pattern.compile(Pattern.quote("http://localhost:8080/" + LINK_MARKER) + "(\\S+)");
    private static final String UUID_EXT = java.util.UUID.randomUUID().toString().replace("-", "").substring(0, 10);

    @Autowired MockMvc mvc;
    @Autowired MailOutboxRepository outbox;
    @Autowired PasswordResetTokenRepository tokens;
    @Autowired UserRepository users;
    @Autowired MailService mailService;
    @Autowired PasswordRecoveryService recovery;
    @Autowired PlatformTransactionManager transactions;
    @MockitoBean JavaMailSender mailSender;

    @BeforeEach
    void cleanOutbox() {
        outbox.deleteAll();
    }

    private String registerUser(String email) throws Exception {
        mvc.perform(post("/cadastro").with(csrf())
                .param("name", "Cliente Mail")
                .param("email", email)
                .param("phone", "(11) 96666-2000")
                .param("password", "SenhaForte123")
                .param("confirmPassword", "SenhaForte123"))
            .andExpect(status().isOk());
        return email;
    }

    @Test
    void recoverySendsEmailViaOutboxAndNeverExposesTheTokenOnAnyPage() throws Exception {
        String email = "mail-flow-" + UUID_EXT + "@example.com";
        registerUser(email);

        // Páginas públicas nunca contêm o marcador de link de reset
        for (String page : new String[]{"/login", "/cadastro", "/recuperar-senha"}) {
            mvc.perform(get(page))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString(LINK_MARKER))));
        }

        mvc.perform(post("/recuperar-senha").with(csrf()).param("email", email))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("enviamos as instruções")))
            .andExpect(content().string(not(containsString(LINK_MARKER))));

        List<MailOutbox> queued = outbox.findAll();
        assertEquals(1, queued.size(), "exatamente um e-mail deve ser enfileirado na mesma transação do token");
        MailOutbox mail = queued.get(0);
        assertEquals(MailOutboxStatus.PENDING, mail.getStatus());
        assertEquals("password-reset", mail.getTemplate());
        assertEquals(email, mail.getToEmail());

        mailService.processDue();

        MailOutbox sent = outbox.findAll().get(0);
        assertEquals(MailOutboxStatus.SENT, sent.getStatus());
        assertNotNull(sent.getSentAt());
        assertEquals(0, sent.getAttempts());

        var captor = org.mockito.ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender).send(captor.capture());
        SimpleMailMessage message = captor.getValue();
        assertEquals(email, message.getTo()[0]);
        String body = message.getText();
        assertTrue(body.contains("PratoJá"));
        assertTrue(body.contains("30 minutos"));
        assertTrue(body.contains(LINK_MARKER));

        // O token que saiu por e-mail não aparece em nenhuma página HTML
        String token = extractToken(body);
        for (String page : new String[]{"/login", "/cadastro", "/recuperar-senha"}) {
            mvc.perform(get(page))
                .andExpect(content().string(not(containsString(token))));
        }

        // Fluxo completo: o link recebido por e-mail realmente redefine a senha
        String nova = "Redefinida" + UUID_EXT.substring(0, 6);
        mvc.perform(post("/redefinir-senha").with(csrf())
                .param("token", token).param("password", nova).param("confirmPassword", nova))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/login"));
        mvc.perform(post("/login").with(csrf()).param("email", email).param("password", nova))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/"));
    }

    private String extractToken(String body) {
        Matcher matcher = TOKEN_IN_EMAIL.matcher(body);
        assertTrue(matcher.find(), "e-mail deve conter o link de redefinição");
        return matcher.group(1);
    }

    @Test
    void outboxRowIsWrittenInTheSameTransactionAsTheToken() throws Exception {
        String email = "mail-tx-" + UUID_EXT + "@example.com";
        registerUser(email);
        var user = users.findByEmailIgnoreCase(email).orElseThrow();

        // Enqueue + token em uma transação que sofre rollback: nada pode persistir
        new TransactionTemplate(transactions).execute(status -> {
            recovery.startReset(email);
            status.setRollbackOnly();
            return null;
        });
        assertEquals(0, outbox.count(), "rollback da transação precisa descartar o outbox");
        assertEquals(0, tokens.findAll().stream().filter(t -> t.getUser().getId().equals(user.getId())).count());

        // Transação efetiva: token e outbox persistem juntos
        assertTrue(recovery.startReset(email));
        assertEquals(1, outbox.count());
        assertEquals(1, tokens.findAll().stream().filter(t -> t.getUser().getId().equals(user.getId())).count());
    }

    @Test
    void unknownEmailIsNotEnqueuedAndResponseIsGeneric() throws Exception {
        mvc.perform(post("/recuperar-senha").with(csrf()).param("email", "fantasma-" + UUID_EXT + "@example.com"))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("Se o e-mail estiver cadastrado")))
            .andExpect(content().string(not(containsString(LINK_MARKER))));
        assertEquals(0, outbox.count(), "e-mail desconhecido não pode gerar outbox (anti enumeração)");
    }

    @Test
    void failedSendRetriesWithExponentialBackoffThenDies() throws Exception {
        String email = "mail-retry-" + UUID_EXT + "@example.com";
        registerUser(email);
        assertTrue(recovery.startReset(email));
        doThrow(new MailSendException("smtp indisponivel")).when(mailSender).send(any(SimpleMailMessage.class));

        long[] expectedBackoffMinutes = {1, 5, 15, 15};
        for (int attempt = 1; attempt <= 4; attempt++) {
            mailService.processDue();
            MailOutbox mail = outbox.findAll().get(0);
            assertEquals(MailOutboxStatus.FAILED, mail.getStatus(), "após falha " + attempt + " deve aguardar retry");
            assertEquals(attempt, mail.getAttempts());
            assertTrue(mail.getLastError().contains("smtp indisponivel"));
            Duration backoff = Duration.between(LocalDateTime.now(), mail.getNextAttemptAt());
            long expectedSeconds = expectedBackoffMinutes[attempt - 1] * 60;
            assertTrue(Math.abs(backoff.toSeconds() - expectedSeconds) < 30,
                "backoff da tentativa " + attempt + " deveria ser ~" + expectedBackoffMinutes[attempt - 1] + "min, foi " + backoff.toSeconds() + "s");

            // Enquanto next_attempt_at está no futuro, o scheduler não reprocessa
            mailService.processDue();
            assertEquals(attempt, outbox.findAll().get(0).getAttempts(), "retry só acontece quando next_attempt_at vence");

            // Adianta o relógio para a próxima tentativa
            mail = outbox.findAll().get(0);
            mail.setNextAttemptAt(LocalDateTime.now().minusSeconds(1));
            outbox.save(mail);
        }

        mailService.processDue();
        MailOutbox dead = outbox.findAll().get(0);
        assertEquals(MailOutboxStatus.DEAD, dead.getStatus(), "após 5 tentativas deve virar DEAD");
        assertEquals(5, dead.getAttempts());
        assertNull(dead.getSentAt());

        // DEAD não é reprocessado
        verify(mailSender, org.mockito.Mockito.times(5)).send(any(SimpleMailMessage.class));
        mailService.processDue();
        verify(mailSender, org.mockito.Mockito.times(5)).send(any(SimpleMailMessage.class));
    }
}

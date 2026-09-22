package br.com.pratoja.pratoja;

import br.com.pratoja.pratoja.domain.DomainTypes;
import br.com.pratoja.pratoja.domain.User;
import br.com.pratoja.pratoja.repository.PasswordResetTokenRepository;
import br.com.pratoja.pratoja.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class PasswordRecoverySecurityTests {
    private static final String DEMO_EMAIL = "cliente@pratoja.com.br";
    private static final String ADMIN_EMAIL = "admin@pratoja.com.br";
    private static final String LINK_MARKER = "redefinir-senha?token=";
    private static final String NEUTRAL_MESSAGE = "Se o e-mail estiver cadastrado";
    private static final String SEEDED_DEMO_PASSWORD = "Cliente@123";
    private static final String TEST_ONLY_ADMIN_PASSWORD = "AdminFicticioApenasTeste#9042-nao-usar";

    @Autowired MockMvc mvc;
    @Autowired UserRepository users;
    @Autowired PasswordResetTokenRepository tokens;
    @Autowired PasswordEncoder encoder;

    private long tokenCount(Long userId) {
        return tokens.findAll().stream().filter(t -> t.getUser().getId().equals(userId)).count();
    }

    @Test
    void demoResetLinkIsExposedOnlyForTheDemoCustomerAccount() throws Exception {
        User demo = users.findByEmailIgnoreCase(DEMO_EMAIL).orElseThrow();
        mvc.perform(post("/recuperar-senha").with(csrf()).param("email", DEMO_EMAIL))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString(LINK_MARKER)))
            .andExpect(content().string(containsString(NEUTRAL_MESSAGE)));
        assertTrue(tokenCount(demo.getId()) >= 1, "a conta demo deve receber um token de reset");
    }

    @Test
    void adminAccountNeverGetsDemoResetLinkOrToken() throws Exception {
        User admin = users.findByEmailIgnoreCase(ADMIN_EMAIL).orElseGet(this::createTestAdminAccount);
        String hashBefore = admin.getPasswordHash();
        mvc.perform(post("/recuperar-senha").with(csrf()).param("email", ADMIN_EMAIL))
            .andExpect(status().isOk())
            .andExpect(content().string(not(containsString(LINK_MARKER))))
            .andExpect(content().string(containsString(NEUTRAL_MESSAGE)));
        assertEquals(0, tokenCount(admin.getId()), "nenhum token pode ser criado para a conta administrativa em demo mode");
        assertEquals(hashBefore, users.findByEmailIgnoreCase(ADMIN_EMAIL).orElseThrow().getPasswordHash(),
            "o hash da conta administrativa não pode mudar");
    }

    // O DataSeeder de produção não cria admin sem PRATOJA_ADMIN_PASSWORD e o CI não define segredo externo:
    // o teste provisiona a própria conta administrativa com senha fictícia, exclusiva deste teste.
    private User createTestAdminAccount() {
        User admin = new User();
        admin.setName("Admin Somente Teste");
        admin.setEmail(ADMIN_EMAIL);
        admin.setPhone("(00) 90000-0000");
        admin.setPasswordHash(encoder.encode(TEST_ONLY_ADMIN_PASSWORD));
        admin.setRole(DomainTypes.Role.ADMIN);
        return users.save(admin);
    }

    @Test
    void otherCustomerAccountsGetNeutralResponseWithoutTokens() throws Exception {
        String email = "sec-" + UUID.randomUUID() + "@example.com";
        mvc.perform(post("/cadastro").with(csrf())
                .param("name", "Cliente Seguranca")
                .param("email", email)
                .param("phone", "(11) 97777-3000")
                .param("password", "SenhaForte123")
                .param("confirmPassword", "SenhaForte123"))
            .andExpect(status().isOk());
        User victim = users.findByEmailIgnoreCase(email).orElseThrow();
        mvc.perform(post("/recuperar-senha").with(csrf()).param("email", email))
            .andExpect(status().isOk())
            .andExpect(content().string(not(containsString(LINK_MARKER))))
            .andExpect(content().string(containsString(NEUTRAL_MESSAGE)));
        assertEquals(0, tokenCount(victim.getId()), "contas comuns não demo não podem receber token em demo mode");
    }

    @Test
    void unknownEmailGetsSameNeutralResponse() throws Exception {
        mvc.perform(post("/recuperar-senha").with(csrf()).param("email", "ninguem-" + UUID.randomUUID() + "@example.com"))
            .andExpect(status().isOk())
            .andExpect(content().string(not(containsString(LINK_MARKER))))
            .andExpect(content().string(containsString(NEUTRAL_MESSAGE)));
    }

    @Test
    void lookalikeEmailsDoNotReceiveTheDemoLink() throws Exception {
        mvc.perform(post("/recuperar-senha").with(csrf()).param("email", DEMO_EMAIL + ".evil.com"))
            .andExpect(status().isOk())
            .andExpect(content().string(not(containsString(LINK_MARKER))));
    }

    @Test
    void demoAccountFullRecoveryFlowRotatesThePassword() throws Exception {
        try {
            MvcResult result = mvc.perform(post("/recuperar-senha").with(csrf()).param("email", DEMO_EMAIL))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString(LINK_MARKER)))
                .andReturn();
            String html = result.getResponse().getContentAsString();
            String token = html.substring(html.indexOf(LINK_MARKER) + LINK_MARKER.length());
            token = token.substring(0, token.indexOf('"'));
            String nova = "RotacaoSegura" + UUID.randomUUID().toString().substring(0, 6);
            mvc.perform(post("/redefinir-senha").with(csrf())
                    .param("token", token).param("password", nova).param("confirmPassword", nova))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));
            mvc.perform(post("/login").with(csrf()).param("email", DEMO_EMAIL).param("password", nova))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/cardapio"));
            mvc.perform(post("/login").with(csrf()).param("email", DEMO_EMAIL).param("password", SEEDED_DEMO_PASSWORD))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login?error"));
        } finally {
            User demo = users.findByEmailIgnoreCase(DEMO_EMAIL).orElseThrow();
            demo.setPasswordHash(encoder.encode(SEEDED_DEMO_PASSWORD));
            users.save(demo);
        }
    }

    @Test
    void forgedTokenCannotResetAnything() throws Exception {
        mvc.perform(post("/redefinir-senha").with(csrf())
                .param("token", "token-forjado-" + UUID.randomUUID())
                .param("password", "AtaqueForte123")
                .param("confirmPassword", "AtaqueForte123"))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("Link inválido ou expirado.")));
    }

    @Test
    void recoveryEndpointsRequireCsrf() throws Exception {
        mvc.perform(post("/recuperar-senha").param("email", DEMO_EMAIL))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/?acesso-negado"));
        mvc.perform(post("/redefinir-senha")
                .param("token", "x").param("password", "QualquerSenha1").param("confirmPassword", "QualquerSenha1"))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/?acesso-negado"));
    }
}

package br.com.pratoja.pratoja;

import br.com.pratoja.pratoja.domain.DomainTypes;
import br.com.pratoja.pratoja.domain.User;
import br.com.pratoja.pratoja.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

// Fluxo de troca obrigatória de senha (must_change_password) e corte de sessão de conta desativada.
@SpringBootTest(properties = {
        "pratoja.recovery.rate-limit.max-attempts-per-ip=1000",
        "pratoja.recovery.rate-limit.max-per-email=1000"
})
@AutoConfigureMockMvc
class ForcedPasswordChangeTests {
    private static final String CENTRAL = "central.it@pratoja.com.br";

    @Autowired MockMvc mvc;
    @Autowired UserRepository users;
    @Autowired PasswordEncoder encoder;

    private User seedCentral(boolean mustChange) {
        users.findByEmailIgnoreCase(CENTRAL).ifPresent(u -> users.deleteById(u.getId()));
        User u = new User();
        u.setName("Central Teste"); u.setEmail(CENTRAL); u.setPhone("(11) 90000-0000");
        u.setPasswordHash(encoder.encode("Temp@12345")); u.setRole(DomainTypes.Role.PLATFORM_ADMIN);
        u.setMustChangePassword(mustChange);
        return users.save(u);
    }

    @Test
    void mustChangeUserIsRedirectedToForceChangeEverywhere() throws Exception {
        seedCentral(true);
        var principal = user(CENTRAL).roles("PLATFORM_ADMIN");

        mvc.perform(get("/admin").with(principal))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/trocar-senha"));
        mvc.perform(get("/platform").with(principal))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/trocar-senha"));
        mvc.perform(get("/").with(principal))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/trocar-senha"));
        // A própria página de troca permanece acessível.
        mvc.perform(get("/trocar-senha").with(principal))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Crie sua senha definitiva")));
    }

    @Test
    void wrongCurrentPasswordKeepsFlagAndShowsError() throws Exception {
        seedCentral(true);
        mvc.perform(post("/trocar-senha").with(user(CENTRAL).roles("PLATFORM_ADMIN")).with(csrf())
                        .param("currentPassword", "Errada@123")
                        .param("password", "NovaSenha@123")
                        .param("confirmPassword", "NovaSenha@123"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Senha atual incorreta")));
        assertTrue(users.findByEmailIgnoreCase(CENTRAL).orElseThrow().isMustChangePassword());
    }

    @Test
    void validChangeClearsFlagHashesNewPasswordAndDropsSession() throws Exception {
        var seeded = seedCentral(true);
        var oldHash = seeded.getPasswordHash();

        mvc.perform(post("/trocar-senha").with(user(CENTRAL).roles("PLATFORM_ADMIN")).with(csrf())
                        .param("currentPassword", "Temp@12345")
                        .param("password", "NovaSenha@123")
                        .param("confirmPassword", "NovaSenha@123"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login?senhaDefinida"));

        var reloaded = users.findByEmailIgnoreCase(CENTRAL).orElseThrow();
        assertFalse(reloaded.isMustChangePassword());
        assertNotEquals(oldHash, reloaded.getPasswordHash());
        assertTrue(encoder.matches("NovaSenha@123", reloaded.getPasswordHash()));

        // Sem a flag, a navegação volta ao normal.
        mvc.perform(get("/admin").with(user(CENTRAL).roles("PLATFORM_ADMIN")))
                .andExpect(status().isOk());
    }

    @Test
    void deactivatedAccountIsCutOffAndSentToLogin() throws Exception {
        var u = seedCentral(false);
        u.setActive(false);
        users.save(u);

        mvc.perform(get("/admin").with(user(CENTRAL).roles("PLATFORM_ADMIN")))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));
    }
}

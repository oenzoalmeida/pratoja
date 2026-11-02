package br.com.pratoja.pratoja;

import br.com.pratoja.pratoja.config.AuthRateLimiter;
import br.com.pratoja.pratoja.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Rate limit de autenticação: POST /login (falhas por IP e por e-mail, com reset no sucesso)
 * e POST /cadastro (tentativas por IP).
 *
 * Limites propositalmente baixos via properties para exercitar os tetos em poucos requests.
 * Banco em memória exclusivo desta classe; demo-mode desligado para não semear a conta demo.
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:auth-rate-limit;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa", "spring.datasource.password=",
        "spring.flyway.locations=classpath:db/migration,classpath:db/migration-h2",
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
        "pratoja.demo-mode=false",
        "pratoja.login.rate-limit.max-failures-per-ip=3",
        "pratoja.login.rate-limit.window-minutes=10",
        "pratoja.login.rate-limit.max-failures-per-email=2",
        "pratoja.login.rate-limit.email-window-minutes=30",
        "pratoja.signup.rate-limit.max-attempts-per-ip=2",
        "pratoja.signup.rate-limit.window-minutes=60"
})
@AutoConfigureMockMvc
class AuthRateLimitTests {
    private static final String SENSIBLE_PASSWORD = "SenhaForte123";

    @Autowired MockMvc mvc;
    @Autowired UserRepository users;
    @Autowired AuthRateLimiter limiter;

    @BeforeEach
    void resetLimiterState() {
        limiter.expireAllWindowsForTests();
    }

    private void registerUser(String email, String ip) throws Exception {
        mvc.perform(post("/cadastro").with(csrf())
                .header("X-Forwarded-For", ip)
                .param("name", "Cliente Rate Limit")
                .param("email", email)
                .param("phone", "(11) 98888-4000")
                .param("password", SENSIBLE_PASSWORD)
                .param("confirmPassword", SENSIBLE_PASSWORD))
            .andExpect(status().isOk());
    }

    private void failLogin(String email, String ip) throws Exception {
        mvc.perform(post("/login").with(csrf())
                .header("X-Forwarded-For", ip)
                .param("email", email).param("password", "SenhaErrada123"))
            .andExpect(status().is3xxRedirection());
    }

    @Test
    void loginBlockedAfterTooManyFailuresFromSameIp() throws Exception {
        String ip = "10.10.0.1";
        // 3 falhas com e-mails DIFERENTES isolam a dimensão por IP (teto por e-mail é 2).
        failLogin("a1@test.dev", ip);
        failLogin("a2@test.dev", ip);
        failLogin("a3@test.dev", ip);
        // 4ª tentativa: bloqueada ANTES de autenticar — redirect para ?limite, não ?error.
        mvc.perform(post("/login").with(csrf())
                .header("X-Forwarded-For", ip)
                .param("email", "a4@test.dev").param("password", "SenhaErrada123"))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/login?limite"));
        // Outro IP segue normal (redirect de credencial inválida).
        mvc.perform(post("/login").with(csrf())
                .header("X-Forwarded-For", "10.10.0.2")
                .param("email", "a4@test.dev").param("password", "SenhaErrada123"))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/login?error"));
    }

    @Test
    void blockedIpCannotAuthenticateEvenWithCorrectPassword() throws Exception {
        String ip = "10.20.0.1";
        registerUser("vitima-ip@test.dev", "10.20.0.9");
        failLogin("b1@test.dev", ip);
        failLogin("b2@test.dev", ip);
        failLogin("b3@test.dev", ip);
        // Com o IP bloqueado, nem a senha correta passa: a tentativa não chega à autenticação.
        mvc.perform(post("/login").with(csrf())
                .header("X-Forwarded-For", ip)
                .param("email", "vitima-ip@test.dev").param("password", SENSIBLE_PASSWORD))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/login?limite"));
    }

    @Test
    void loginBlockedPerEmailAcrossRotatingIps() throws Exception {
        // Mesma conta, IPs diferentes (entrada mais à direita do XFF): a dimensão por e-mail segura.
        failLogin("alvo@test.dev", "10.30.0.1");
        failLogin("alvo@test.dev", "10.30.0.2");
        // 3ª tentativa contra a mesma conta, de um terceiro IP: bloqueada pelo bucket por e-mail.
        mvc.perform(post("/login").with(csrf())
                .header("X-Forwarded-For", "10.30.0.3")
                .param("email", "ALVO@test.dev").param("password", "SenhaErrada123"))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/login?limite"));
        // Outra conta, mesmo IP novo: resposta normal de credencial inválida.
        mvc.perform(post("/login").with(csrf())
                .header("X-Forwarded-For", "10.30.0.3")
                .param("email", "outro@test.dev").param("password", "SenhaErrada123"))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/login?error"));
    }

    @Test
    void successfulLoginResetsFailureBuckets() throws Exception {
        String ip = "10.40.0.1";
        registerUser("legitimo@test.dev", "10.40.0.9");
        failLogin("legitimo@test.dev", ip); // 1 falha: abaixo do teto por e-mail (2), conta ainda não bloqueia
        // Usuário acerta a senha antes do teto: sucesso (redirect "/") e buckets zerados.
        mvc.perform(post("/login").with(csrf())
                .header("X-Forwarded-For", ip)
                .param("email", "legitimo@test.dev").param("password", SENSIBLE_PASSWORD))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/"));
        // Prova do reset: sem ele, o bucket por e-mail já estaria no teto (2) e esta tentativa
        // sairia ?limite. Com o reset, volta a ser a resposta normal de credencial inválida.
        mvc.perform(post("/login").with(csrf())
                .header("X-Forwarded-For", ip)
                .param("email", "legitimo@test.dev").param("password", "SenhaErrada123"))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/login?error"));
    }

    @Test
    void interleavedSuccessDoesNotResetSharedIpBucket() throws Exception {
        String ip = "10.60.0.1";
        registerUser("atacante@test.dev", "10.60.0.9"); // conta válida do próprio atacante
        // Falhas contra OUTRAS contas intercaladas com sucesso da conta válida:
        // o sucesso zera só o bucket do e-mail autenticado, NUNCA o do IP compartilhado.
        failLogin("x1@test.dev", ip);                       // ip=1
        mvc.perform(post("/login").with(csrf()).header("X-Forwarded-For", ip)
                .param("email", "atacante@test.dev").param("password", SENSIBLE_PASSWORD))
            .andExpect(redirectedUrl("/"));
        failLogin("x2@test.dev", ip);                       // ip=2
        mvc.perform(post("/login").with(csrf()).header("X-Forwarded-For", ip)
                .param("email", "atacante@test.dev").param("password", SENSIBLE_PASSWORD))
            .andExpect(redirectedUrl("/"));
        failLogin("x3@test.dev", ip);                       // ip=3 (teto)
        // Se o sucesso limpasse o bucket do IP, esta tentativa sairia ?error — bypass fechado.
        mvc.perform(post("/login").with(csrf())
                .header("X-Forwarded-For", ip)
                .param("email", "x4@test.dev").param("password", "SenhaErrada123"))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/login?limite"));
    }

    @Test
    void signupBlockedAfterTooManyAttemptsFromSameIp() throws Exception {
        String ip = "10.50.0.1";
        registerUser("cad1@test.dev", ip);
        registerUser("cad2@test.dev", ip);
        // 3ª tentativa de cadastro do mesmo IP na janela: não cria conta, mesma view com aviso.
        mvc.perform(post("/cadastro").with(csrf())
                .header("X-Forwarded-For", ip)
                .param("name", "Cliente Bloqueado")
                .param("email", "cad3@test.dev")
                .param("phone", "(11) 98888-4000")
                .param("password", SENSIBLE_PASSWORD)
                .param("confirmPassword", SENSIBLE_PASSWORD))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("Muitas tentativas de cadastro")));
        assertTrue(users.findByEmailIgnoreCase("cad3@test.dev").isEmpty());
        assertEquals(2, users.findAll().stream()
                .filter(u -> u.getEmail().startsWith("cad")).count());
        // Outro IP cadastra normalmente.
        registerUser("cad3@test.dev", "10.50.0.2");
        assertTrue(users.findByEmailIgnoreCase("cad3@test.dev").isPresent());
    }
}

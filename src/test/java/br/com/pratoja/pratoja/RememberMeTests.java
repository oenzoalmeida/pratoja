package br.com.pratoja.pratoja;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * "Manter conectado": POST /login com o parâmetro remember-me emite o cookie
 * PRATOJA_REMEMBER_ME e grava o token persistente; sem o parâmetro, nada disso.
 * Banco em memória exclusivo desta classe; demo-mode desligado.
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:remember-me;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa", "spring.datasource.password=",
        "spring.flyway.locations=classpath:db/migration,classpath:db/migration-h2",
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
        "pratoja.demo-mode=false"
})
@AutoConfigureMockMvc
class RememberMeTests {
    private static final String PASSWORD = "SenhaForte123";

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;

    private void register(String email) throws Exception {
        mvc.perform(post("/cadastro").with(csrf())
                .param("name", "Cliente Remember")
                .param("email", email)
                .param("phone", "(11) 98888-4000")
                .param("password", PASSWORD)
                .param("confirmPassword", PASSWORD))
            .andExpect(status().isOk());
    }

    @Test
    void loginComManterConectadoEmiteCookieEGravaToken() throws Exception {
        String email = "lembrar@test.dev";
        register(email);
        MvcResult result = mvc.perform(post("/login").with(csrf())
                .param("email", email).param("password", PASSWORD)
                .param("remember-me", "true"))
            .andExpect(status().is3xxRedirection())
            .andReturn();
        Cookie cookie = result.getResponse().getCookie("PRATOJA_REMEMBER_ME");
        assertNotNull(cookie, "cookie de remember-me ausente no login com a caixa marcada");
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM persistent_logins WHERE username = ?", Integer.class, email);
        assertEquals(1, count, "token persistente não gravado na tabela persistent_logins");
    }

    @Test
    void loginSemManterConectadoNaoEmiteCookie() throws Exception {
        String email = "nao-lembrar@test.dev";
        register(email);
        MvcResult result = mvc.perform(post("/login").with(csrf())
                .param("email", email).param("password", PASSWORD))
            .andExpect(status().is3xxRedirection())
            .andReturn();
        assertNull(result.getResponse().getCookie("PRATOJA_REMEMBER_ME"),
                "cookie de remember-me não deveria existir sem a caixa marcada");
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM persistent_logins WHERE username = ?", Integer.class, email);
        assertEquals(0, count, "nenhum token persistente deveria ser gravado sem a caixa marcada");
    }
}

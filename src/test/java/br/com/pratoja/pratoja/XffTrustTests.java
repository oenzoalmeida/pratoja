package br.com.pratoja.pratoja;

import br.com.pratoja.pratoja.config.AuthRateLimiter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Confiança no X-Forwarded-For: com pratoja.security.trusted-proxy-hops=0 (nenhum proxy real na
 * frente da app), o header é IGNORADO e a identidade é o remoteAddr. Um cliente que forja um IP
 * diferente a cada tentativa NÃO ganha buckets novos: as falhas acumulam no bucket do remoteAddr.
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:xff-trust;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa", "spring.datasource.password=",
        "spring.flyway.locations=classpath:db/migration,classpath:db/migration-h2",
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
        "pratoja.demo-mode=false",
        "pratoja.security.trusted-proxy-hops=0",
        "pratoja.login.rate-limit.max-failures-per-ip=3",
        "pratoja.login.rate-limit.max-failures-per-email=100"
})
@AutoConfigureMockMvc
class XffTrustTests {

    @Autowired MockMvc mvc;
    @Autowired AuthRateLimiter limiter;

    @BeforeEach
    void resetLimiterState() {
        limiter.expireAllWindowsForTests();
    }

    @Test
    void forgedXffDoesNotCreateFreshBucketsWhenNoTrustedProxy() throws Exception {
        // Cada tentativa forja um XFF diferente; sem proxy confiável, tudo cai no bucket do remoteAddr.
        for (int i = 1; i <= 3; i++) {
            mvc.perform(post("/login").with(csrf())
                    .header("X-Forwarded-For", "203.0.113." + i)
                    .param("email", "alvo" + i + "@test.dev").param("password", "SenhaErrada123"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login?error"));
        }
        mvc.perform(post("/login").with(csrf())
                .header("X-Forwarded-For", "203.0.113.99")
                .param("email", "alvo4@test.dev").param("password", "SenhaErrada123"))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/login?limite"));
    }
}

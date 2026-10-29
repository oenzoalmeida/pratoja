package br.com.pratoja.pratoja;

import br.com.pratoja.pratoja.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Banco em memória exclusivo desta classe: o DataSeeder roda no boot do contexto.
 * Com demo-mode DESLIGADO (produção), a conta de demonstração de senha conhecida
 * NÃO pode ser criada.
 */
@SpringBootTest(properties = {
        "pratoja.demo-mode=false",
        "spring.datasource.url=jdbc:h2:mem:demo-not-seeded;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa", "spring.datasource.password="})
class DemoCustomerNotSeededTests {
    @Autowired UserRepository users;

    @Test
    void demoCustomerIsNotSeededWhenDemoModeOff() {
        assertTrue(users.findByEmailIgnoreCase("cliente@pratoja.com.br").isEmpty());
    }
}

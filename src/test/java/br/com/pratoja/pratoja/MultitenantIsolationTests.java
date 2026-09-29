package br.com.pratoja.pratoja;

import br.com.pratoja.pratoja.domain.*;
import br.com.pratoja.pratoja.repository.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Fases 1-3: isolamento multitenant. Uma segunda loja é criada nos testes e um STORE_ADMIN
 * da loja 1 não pode acessar/editar/excluir objetos da loja 2 (sempre 404, nunca 403).
 */
@SpringBootTest
@AutoConfigureMockMvc
class MultitenantIsolationTests {
    @Autowired MockMvc mvc;
    @Autowired UserRepository users;
    @Autowired StoreRepository stores;
    @Autowired CategoryRepository categories;
    @Autowired ProductRepository products;
    @Autowired OrderRepository orders;
    @Autowired PasswordEncoder encoder;

    private record Fixture(Store store2, Category cat2, Product prod2, User admin1, User admin2) {}

    private Fixture fixture() {
        // Loja 2 (a loja 1 é a migrada pela V6, usada pelo DataSeeder)
        Store s2 = stores.findBySlug("loja-teste-2").orElseGet(() -> {
            Store s = new Store();
            s.setSlug("loja-teste-2"); s.setName("Loja Teste 2"); s.setDeliveryFee(new BigDecimal("5.00"));
            return stores.save(s);
        });
        // Mesmo nome de categoria da loja 1 em outra loja deve ser permitido (UNIQUE por loja, V7)
        Category c2 = categories.findByNameIgnoreCaseAndStore_Id("Bebidas", s2.getId()).orElseGet(() -> {
            Category c = new Category(); c.setStore(s2); c.setName("Bebidas"); c.setSortOrder(1);
            return categories.save(c);
        });
        Product p2 = products.findFirstByStore_IdAndCustomizableTrueAndArchivedFalse(s2.getId()).orElse(null);
        if (p2 == null) {
            p2 = new Product(); p2.setStore(s2); p2.setCategory(c2); p2.setName("Suco da Loja 2");
            p2.setDescription("Produto exclusivo da loja 2."); p2.setPrice(new BigDecimal("9.90"));
            p2 = products.save(p2);
        }
        User a1 = users.findByEmailIgnoreCase("admin1-mt@example.com").orElseGet(() -> {
            User u = new User(); u.setName("Admin Loja 1"); u.setEmail("admin1-mt@example.com");
            u.setPhone("(11) 90000-0001"); u.setPasswordHash(encoder.encode("SenhaForte123"));
            u.setRole(DomainTypes.Role.STORE_ADMIN); u.setStore(stores.findById(1L).orElseThrow());
            return users.save(u);
        });
        User a2 = users.findByEmailIgnoreCase("admin2-mt@example.com").orElseGet(() -> {
            User u = new User(); u.setName("Admin Loja 2"); u.setEmail("admin2-mt@example.com");
            u.setPhone("(11) 90000-0002"); u.setPasswordHash(encoder.encode("SenhaForte123"));
            u.setRole(DomainTypes.Role.STORE_ADMIN); u.setStore(s2);
            return users.save(u);
        });
        return new Fixture(s2, c2, p2, a1, a2);
    }

    private Order orderInStore2(Fixture f) {
        Order o = new Order();
        o.setStore(f.store2()); o.setUser(f.admin2()); o.setFulfillmentType(DomainTypes.FulfillmentType.PICKUP);
        o.setStatus(DomainTypes.OrderStatus.RECEIVED); o.setSubtotal(new BigDecimal("9.90"));
        o.setDeliveryFee(BigDecimal.ZERO); o.setTotal(new BigDecimal("9.90"));
        Payment pay = new Payment(); pay.setOrder(o); pay.setMethod(DomainTypes.PaymentMethod.PIX);
        pay.setStatus(DomainTypes.PaymentStatus.CONFIRMED); o.setPayment(pay);
        return orders.save(o);
    }

    @Test
    void store1AdminGets404OnStore2ProductAndCategory() throws Exception {
        Fixture f = fixture();
        var admin1 = user(f.admin1().getEmail()).roles("STORE_ADMIN");
        mvc.perform(get("/admin/produtos/" + f.prod2().getId() + "/editar").with(admin1)).andExpect(status().isNotFound());
        mvc.perform(post("/admin/produtos/" + f.prod2().getId() + "/excluir").with(admin1).with(csrf())).andExpect(status().isNotFound());
        mvc.perform(post("/admin/produtos/" + f.prod2().getId() + "/disponibilidade").with(admin1).with(csrf())).andExpect(status().isNotFound());
        mvc.perform(post("/admin/categorias/" + f.cat2().getId() + "/status").with(admin1).with(csrf())).andExpect(status().isNotFound());
        // e não consegue mover produto da loja 2 para uma categoria da própria loja 1 via salvar
        Category cat1 = categories.findByStore_IdOrderBySortOrderAscNameAsc(1L).get(0);
        mvc.perform(post("/admin/produtos/salvar").with(admin1).with(csrf())
                        .param("id", f.prod2().getId().toString()).param("name", "Hackeado")
                        .param("description", "x").param("price", "1.00").param("categoryId", cat1.getId().toString()))
                .andExpect(status().isNotFound());
        assertNotNull(products.findByIdAndStore_Id(f.prod2().getId(), f.store2().getId()).orElseThrow().getCategory());
    }

    @Test
    void store1AdminGets404OnStore2Orders() throws Exception {
        Fixture f = fixture();
        Order o2 = orderInStore2(f);
        var admin1 = user(f.admin1().getEmail()).roles("STORE_ADMIN");
        mvc.perform(get("/admin/pedidos/" + o2.getId()).with(admin1)).andExpect(status().isNotFound());
        mvc.perform(post("/admin/pedidos/" + o2.getId() + "/status").with(admin1).with(csrf())
                        .param("status", "CONFIRMED")).andExpect(status().isNotFound());
    }

    @Test
    void crossTenantRepeatIsRejected() throws Exception {
        Fixture f = fixture();
        Order o2 = orderInStore2(f);
        // Cliente da loja 1 tentando repetir pedido que pertence à loja 2 -> 404
        mvc.perform(post("/pedidos/" + o2.getId() + "/repetir")
                        .with(user("cliente@pratoja.com.br").roles("CUSTOMER")).with(csrf()))
                .andExpect(status().isNotFound());
    }

    @Test
    void sameCategoryNameIsAllowedInDifferentStores() {
        Fixture f = fixture();
        assertNotNull(categories.findByNameIgnoreCaseAndStore_Id("Bebidas", 1L).orElseThrow());
        assertNotNull(categories.findByNameIgnoreCaseAndStore_Id("Bebidas", f.store2().getId()).orElseThrow());
    }

    @Test
    void platformAdminPathsAreProtected() throws Exception {
        Fixture f = fixture();
        // STORE_ADMIN não acessa /platform (Fase 5); acesso negado redireciona
        mvc.perform(get("/platform").with(user(f.admin1().getEmail()).roles("STORE_ADMIN")))
                .andExpect(status().is3xxRedirection());
        // Anônimo é redirecionado para o login
        mvc.perform(get("/admin")).andExpect(status().is3xxRedirection());
    }
}

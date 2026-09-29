package br.com.pratoja.pratoja;

import br.com.pratoja.pratoja.domain.*;
import br.com.pratoja.pratoja.cart.CartLine;
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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.*;

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
    @Autowired br.com.pratoja.pratoja.service.OrderService orderService;
    @Autowired br.com.pratoja.pratoja.cart.SessionCart cart;
    @Autowired br.com.pratoja.pratoja.cart.CartService cartService;

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
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/?acesso-negado"));
        mvc.perform(get("/platform").with(user("cliente@pratoja.com.br").roles("CUSTOMER")))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/?acesso-negado"));
        // Anônimo é redirecionado para o login
        mvc.perform(get("/platform")).andExpect(status().is3xxRedirection());
        mvc.perform(get("/admin")).andExpect(status().is3xxRedirection());
        // PLATFORM_ADMIN acessa o painel
        User plat = platformAdmin();
        mvc.perform(get("/platform").with(user(plat.getEmail()).roles("PLATFORM_ADMIN")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Lojas")));
    }

    @Test
    void inactiveStoreIsUnavailableOnPublicRoutes() throws Exception {
        Fixture f = fixture();
        Store s2 = stores.findById(f.store2().getId()).orElseThrow();
        boolean wasActive = s2.isActive();
        try {
            s2.setActive(false); stores.save(s2);
            mvc.perform(get("/loja/loja-teste-2")).andExpect(status().isNotFound());
            mvc.perform(get("/loja/loja-teste-2/cardapio")).andExpect(status().isNotFound());
            mvc.perform(get("/loja/loja-teste-2/api/cart")).andExpect(status().isNotFound());
        } finally {
            s2.setActive(wasActive); stores.save(s2);
        }
        // Reativada, volta a ficar disponível
        mvc.perform(get("/loja/loja-teste-2")).andExpect(status().isOk());
    }

    @Test
    void cartIsScopedPerStore() throws Exception {
        Long sid1 = 1L, sid2 = stores.findBySlug("loja-teste-2").orElseThrow().getId();
        cart.clear(sid1); cart.clear(sid2);
        // Item da loja 1 adicionado na sacola da loja 1 (via API da loja)
        mvc.perform(post("/loja/restaurante/api/cart/items").with(csrf())
                        .contentType("application/json")
                        .content("{\"productId\":1,\"quantity\":1,\"optionIds\":[],\"notes\":\"\"}"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("\"count\":1")));
        // A sacola da loja 2 continua vazia (e adicionar o produto da loja 1 nela falha)
        mvc.perform(get("/loja/loja-teste-2/api/cart"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("\"count\":0")));
        mvc.perform(post("/loja/loja-teste-2/api/cart/items").with(csrf())
                        .contentType("application/json")
                        .content("{\"productId\":1,\"quantity\":1,\"optionIds\":[],\"notes\":\"\"}"))
                .andExpect(status().isBadRequest());
        cart.clear(sid1); cart.clear(sid2);
    }

    @Test
    void placeWithProductFromAnotherStoreIsRejected() {
        var f = fixture();
        Long sid1 = 1L, sid2 = f.store2().getId();
        cart.clear(sid2);
        // Linha montada com produto da loja 1, injetada na sacola da loja 2 (cenário adversarial)
        CartLine line = cartService.create(sid1, 1L, 1, java.util.List.of(), "");
        cart.add(sid2, line);
        var auth = new org.springframework.security.authentication.UsernamePasswordAuthenticationToken("cliente@pratoja.com.br", "n/a", java.util.List.of());
        assertThrows(SecurityException.class, () ->
                orderService.place(auth, stores.findById(sid2).orElseThrow(), "PICKUP", null, "PIX", null, null));
        cart.clear(sid2);
    }

    @Test
    void trackingAndRepeatRequireStoreCoherentRoute() throws Exception {
        // Pedido do cliente na loja 1
        User cliente = users.findByEmailIgnoreCase("cliente@pratoja.com.br").orElseThrow();
        Order o1 = new Order();
        o1.setStore(stores.findById(1L).orElseThrow()); o1.setUser(cliente);
        o1.setFulfillmentType(DomainTypes.FulfillmentType.PICKUP); o1.setStatus(DomainTypes.OrderStatus.RECEIVED);
        o1.setSubtotal(new BigDecimal("9.90")); o1.setDeliveryFee(BigDecimal.ZERO); o1.setTotal(new BigDecimal("9.90"));
        Payment pay = new Payment(); pay.setOrder(o1); pay.setMethod(DomainTypes.PaymentMethod.PIX);
        pay.setStatus(DomainTypes.PaymentStatus.CONFIRMED); o1.setPayment(pay);
        o1 = orders.save(o1);
        // Acessar o pedido da loja 1 pela rota da loja 2 -> 404
        mvc.perform(get("/loja/loja-teste-2/pedidos/" + o1.getId() + "/acompanhar")
                        .with(user("cliente@pratoja.com.br").roles("CUSTOMER")))
                .andExpect(status().isNotFound());
        mvc.perform(post("/loja/loja-teste-2/pedidos/" + o1.getId() + "/repetir")
                        .with(user("cliente@pratoja.com.br").roles("CUSTOMER")).with(csrf()))
                .andExpect(status().isNotFound());
        // Pela rota correta da loja 1, o dono acessa
        mvc.perform(get("/loja/restaurante/pedidos/" + o1.getId() + "/acompanhar")
                        .with(user("cliente@pratoja.com.br").roles("CUSTOMER")))
                .andExpect(status().isOk());
        // Cliente diferente não acessa (regra de dono mantida)
        mvc.perform(get("/loja/restaurante/pedidos/" + o1.getId() + "/acompanhar")
                        .with(user("admin1-mt@example.com").roles("STORE_ADMIN")))
                .andExpect(status().isNotFound());
    }

    @Test
    void store2AdminDoesNotSeeStore1Orders() throws Exception {
        Fixture f = fixture();
        var store1Orders = orders.findAllByStore_IdOrderByCreatedAtDesc(1L);
        var admin2 = user(f.admin2().getEmail()).roles("STORE_ADMIN");
        var page = mvc.perform(get("/admin/pedidos").with(admin2))
                .andExpect(status().isOk());
        for (Order o1 : store1Orders) {
            page.andExpect(content().string(not(containsString("#" + String.format("%04d", o1.getId())))));
        }
    }

    @Test
    void platformCreateStoreValidatesUniqueSlug() throws Exception {
        User plat = platformAdmin();
        var platUser = user(plat.getEmail()).roles("PLATFORM_ADMIN");
        // Slug duplicado -> erro de validação (flash de erro), nenhuma loja criada
        long before = stores.count();
        mvc.perform(post("/platform/lojas").with(platUser).with(csrf())
                        .param("name", "Outra Loja").param("slug", "restaurante"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/platform/lojas/nova"));
        assertEquals(before, stores.count());
        // Slug único -> loja criada (ativa por padrão)
        mvc.perform(post("/platform/lojas").with(platUser).with(csrf())
                        .param("name", "Loja Criada Via Platform").param("slug", "loja-criada-via-platform"))
                .andExpect(status().is3xxRedirection());
        Store created = stores.findBySlug("loja-criada-via-platform").orElseThrow();
        assertTrue(created.isActive());
        // Slug gerado do nome quando não informado
        mvc.perform(post("/platform/lojas").with(platUser).with(csrf())
                        .param("name", "Pizzaria do Bairro"))
                .andExpect(status().is3xxRedirection());
        assertTrue(stores.findBySlug("pizzaria-do-bairro").isPresent());
    }

    private User platformAdmin() {
        return users.findByEmailIgnoreCase("platform-mt@example.com").orElseGet(() -> {
            User u = new User(); u.setName("Plataforma MT"); u.setEmail("platform-mt@example.com");
            u.setPhone("(11) 90000-0003"); u.setPasswordHash(encoder.encode("SenhaForte123"));
            u.setRole(DomainTypes.Role.PLATFORM_ADMIN); u.setStore(null);
            return users.save(u);
        });
    }
}

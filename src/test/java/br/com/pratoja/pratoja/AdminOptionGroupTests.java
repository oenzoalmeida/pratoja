package br.com.pratoja.pratoja;

import br.com.pratoja.pratoja.domain.*;
import br.com.pratoja.pratoja.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Painel de personalização (/admin/opcoes): o restaurante gerencia grupos e opções do
 * "Monte seu prato" da PRÓPRIA loja. Grupo/opção de outra loja -> 404 (nunca 403),
 * validações de min/max e proteção do histórico de pedidos (opção referenciada não é excluída).
 */
@SpringBootTest
@AutoConfigureMockMvc
class AdminOptionGroupTests {
    @Autowired MockMvc mvc;
    @Autowired UserRepository users;
    @Autowired StoreRepository stores;
    @Autowired CategoryRepository categories;
    @Autowired ProductRepository products;
    @Autowired OptionGroupRepository groups;
    @Autowired ProductOptionRepository options;
    @Autowired OrderRepository orders;
    @Autowired OrderItemOptionRepository orderItemOptions;
    @Autowired PasswordEncoder encoder;

    private Store store1;
    private Product custom1;

    @BeforeEach
    void fixture() {
        store1 = stores.findById(1L).orElseThrow();
        users.findByEmailIgnoreCase("admin@pratoja.com.br").orElseGet(() -> {
            User u = new User(); u.setName("Admin Loja 1"); u.setEmail("admin@pratoja.com.br");
            u.setPhone("(11) 99999-1000"); u.setPasswordHash(encoder.encode("SenhaForte123"));
            u.setRole(DomainTypes.Role.STORE_ADMIN); u.setStore(store1);
            return users.save(u);
        });
        custom1 = products.findFirstByStore_IdAndCustomizableTrueAndArchivedFalse(store1.getId()).orElseThrow();
    }

    private Store store2WithCustomProduct() {
        Store s2 = stores.findBySlug("loja-opcoes-2").orElseGet(() -> {
            Store s = new Store();
            s.setSlug("loja-opcoes-2"); s.setName("Loja Opções 2"); s.setDeliveryFee(new BigDecimal("5.00"));
            return stores.save(s);
        });
        Category c2 = categories.findByNameIgnoreCaseAndStore_Id("Monte seu prato", s2.getId()).orElseGet(() -> {
            Category c = new Category(); c.setStore(s2); c.setName("Monte seu prato"); c.setSortOrder(0);
            return categories.save(c);
        });
        if (products.findFirstByStore_IdAndCustomizableTrueAndArchivedFalse(s2.getId()).isEmpty()) {
            Product p = new Product(); p.setStore(s2); p.setCategory(c2); p.setName("Monte da Loja 2");
            p.setDescription("Personalizável da loja 2."); p.setPrice(new BigDecimal("15.90")); p.setCustomizable(true);
            products.save(p);
        }
        return s2;
    }

    @Test
    void optionsPageRendersForStoreAdmin() throws Exception {
        mvc.perform(get("/admin/opcoes").with(user("admin@pratoja.com.br").roles("STORE_ADMIN")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Personalização do prato")))
                .andExpect(content().string(containsString("Escolha a base")));
    }

    @Test
    void optionsPageRequiresAdminRole() throws Exception {
        mvc.perform(get("/admin/opcoes")).andExpect(status().is3xxRedirection());
        mvc.perform(get("/admin/opcoes").with(user("cliente@example.com").roles("CUSTOMER")))
                .andExpect(status().is3xxRedirection());
    }

    @Test
    void createEditAndDeleteGroupWithinOwnStore() throws Exception {
        mvc.perform(post("/admin/opcoes/grupos").with(user("admin@pratoja.com.br").roles("STORE_ADMIN")).with(csrf())
                        .param("productId", String.valueOf(custom1.getId()))
                        .param("type", "EXTRA").param("name", "Teste extras")
                        .param("minSelections", "0").param("maxSelections", "2").param("sortOrder", "9"))
                .andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/admin/opcoes"));
        OptionGroup g = groups.findByStore_IdOrderBySortOrder(store1.getId()).stream()
                .filter(x -> x.getName().equals("Teste extras")).findFirst().orElseThrow();
        assertEquals(store1.getId(), g.getStore().getId());

        mvc.perform(post("/admin/opcoes/grupos/" + g.getId() + "/editar").with(user("admin@pratoja.com.br").roles("STORE_ADMIN")).with(csrf())
                        .param("type", "EXTRA").param("name", "Teste extras editado")
                        .param("minSelections", "1").param("maxSelections", "3").param("sortOrder", "8"))
                .andExpect(status().is3xxRedirection());
        OptionGroup edited = groups.findById(g.getId()).orElseThrow();
        assertEquals("Teste extras editado", edited.getName());
        assertEquals(1, edited.getMinSelections());
        assertEquals(3, edited.getMaxSelections());

        mvc.perform(post("/admin/opcoes/grupos/" + g.getId() + "/excluir").with(user("admin@pratoja.com.br").roles("STORE_ADMIN")).with(csrf()))
                .andExpect(status().is3xxRedirection());
        assertTrue(groups.findById(g.getId()).isEmpty());
    }

    @Test
    void invalidGroupBoundsAreRejected() throws Exception {
        long before = groups.count();
        mvc.perform(post("/admin/opcoes/grupos").with(user("admin@pratoja.com.br").roles("STORE_ADMIN")).with(csrf())
                        .param("productId", String.valueOf(custom1.getId()))
                        .param("type", "EXTRA").param("name", "Grupo inválido")
                        .param("minSelections", "3").param("maxSelections", "1"))
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attributeExists("error"));
        assertEquals(before, groups.count());
    }

    @Test
    void crossTenantGroupAndOptionAccessReturns404() throws Exception {
        Store s2 = store2WithCustomProduct();
        Product p2 = products.findFirstByStore_IdAndCustomizableTrueAndArchivedFalse(s2.getId()).orElseThrow();
        OptionGroup g2 = new OptionGroup();
        g2.setStore(s2); g2.setProduct(p2); g2.setType(DomainTypes.OptionType.BASE);
        g2.setName("Base da Loja 2"); g2.setMinSelections(1); g2.setMaxSelections(1); g2.setSortOrder(1);
        g2 = groups.save(g2);
        ProductOption o2 = new ProductOption();
        o2.setGroup(g2); o2.setName("Opção da Loja 2"); o2.setPriceDelta(BigDecimal.ZERO); o2.setSortOrder(1);
        o2 = options.save(o2);

        // Admin da loja 1 tentando editar grupo/opção da loja 2 -> 404
        mvc.perform(post("/admin/opcoes/grupos/" + g2.getId() + "/editar").with(user("admin@pratoja.com.br").roles("STORE_ADMIN")).with(csrf())
                        .param("type", "BASE").param("name", "Invasão").param("minSelections", "1").param("maxSelections", "1"))
                .andExpect(status().isNotFound());
        assertEquals("Base da Loja 2", groups.findById(g2.getId()).orElseThrow().getName());

        mvc.perform(post("/admin/opcoes/itens/" + o2.getId() + "/editar").with(user("admin@pratoja.com.br").roles("STORE_ADMIN")).with(csrf())
                        .param("name", "Invasão").param("priceDelta", "1.00"))
                .andExpect(status().isNotFound());
        mvc.perform(post("/admin/opcoes/itens/" + o2.getId() + "/disponibilidade").with(user("admin@pratoja.com.br").roles("STORE_ADMIN")).with(csrf()))
                .andExpect(status().isNotFound());
        mvc.perform(post("/admin/opcoes/grupos/" + g2.getId() + "/excluir").with(user("admin@pratoja.com.br").roles("STORE_ADMIN")).with(csrf()))
                .andExpect(status().isNotFound());
        assertTrue(options.findById(o2.getId()).orElseThrow().isAvailable());

        // Grupo da loja 2 não pode ser criado apontando produto da loja 1 por outro admin: coberto pela
        // validação de produto da própria loja no create (productId de outra loja -> 404).
        mvc.perform(post("/admin/opcoes/grupos").with(user("admin@pratoja.com.br").roles("STORE_ADMIN")).with(csrf())
                        .param("productId", String.valueOf(p2.getId()))
                        .param("type", "BASE").param("name", "Grupo indevido")
                        .param("minSelections", "1").param("maxSelections", "1"))
                .andExpect(status().isNotFound());
    }

    @Test
    void optionLifecycleWithinOwnStore() throws Exception {
        OptionGroup g = groups.findByProductIdOrderBySortOrder(custom1.getId()).get(0);
        mvc.perform(post("/admin/opcoes/grupos/" + g.getId() + "/itens").with(user("admin@pratoja.com.br").roles("STORE_ADMIN")).with(csrf())
                        .param("name", "Opção teste ciclo").param("priceDelta", "2.50").param("sortOrder", "99"))
                .andExpect(status().is3xxRedirection());
        ProductOption o = options.findByGroupIdOrderBySortOrder(g.getId()).stream()
                .filter(x -> x.getName().equals("Opção teste ciclo")).findFirst().orElseThrow();
        assertEquals(new BigDecimal("2.50"), o.getPriceDelta());

        mvc.perform(post("/admin/opcoes/itens/" + o.getId() + "/disponibilidade").with(user("admin@pratoja.com.br").roles("STORE_ADMIN")).with(csrf()))
                .andExpect(status().is3xxRedirection());
        assertFalse(options.findById(o.getId()).orElseThrow().isAvailable());

        mvc.perform(post("/admin/opcoes/itens/" + o.getId() + "/editar").with(user("admin@pratoja.com.br").roles("STORE_ADMIN")).with(csrf())
                        .param("name", "Opção teste renomeada").param("priceDelta", "3.00").param("sortOrder", "98"))
                .andExpect(status().is3xxRedirection());
        ProductOption renamed = options.findById(o.getId()).orElseThrow();
        assertEquals("Opção teste renomeada", renamed.getName());

        mvc.perform(post("/admin/opcoes/itens/" + o.getId() + "/excluir").with(user("admin@pratoja.com.br").roles("STORE_ADMIN")).with(csrf()))
                .andExpect(status().is3xxRedirection());
        assertTrue(options.findById(o.getId()).isEmpty());
    }

    @Test
    void referencedOptionCannotBeDeletedOnlyUnavailable() throws Exception {
        OptionGroup g = groups.findByProductIdOrderBySortOrder(custom1.getId()).get(0);
        ProductOption o = new ProductOption();
        o.setGroup(g); o.setName("Opção referenciada " + System.nanoTime()); o.setPriceDelta(new BigDecimal("1.00")); o.setSortOrder(100);
        o = options.save(o);

        // Pedido histórico referenciando a opção
        User customer = users.findByEmailIgnoreCase("cliente@pratoja.com.br").orElseThrow();
        Order order = new Order();
        order.setStore(store1); order.setUser(customer); order.setFulfillmentType(DomainTypes.FulfillmentType.PICKUP);
        order.setStatus(DomainTypes.OrderStatus.DELIVERED); order.setSubtotal(new BigDecimal("16.90"));
        order.setDeliveryFee(BigDecimal.ZERO); order.setTotal(new BigDecimal("16.90"));
        Payment pay = new Payment(); pay.setOrder(order); pay.setMethod(DomainTypes.PaymentMethod.PIX);
        pay.setStatus(DomainTypes.PaymentStatus.CONFIRMED); order.setPayment(pay);
        OrderItem item = new OrderItem();
        item.setOrder(order); item.setProduct(custom1); item.setProductName(custom1.getName());
        item.setUnitPrice(custom1.getPrice()); item.setQuantity(1); item.setLineTotal(new BigDecimal("16.90"));
        OrderItemOption ref = new OrderItemOption();
        ref.setOrderItem(item); ref.setOption(o); ref.setGroupName(g.getName()); ref.setOptionName(o.getName());
        ref.setPriceDelta(o.getPriceDelta());
        item.getOptions().add(ref);
        order.getItems().add(item);
        orders.save(order);

        mvc.perform(post("/admin/opcoes/itens/" + o.getId() + "/excluir").with(user("admin@pratoja.com.br").roles("STORE_ADMIN")).with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attributeExists("error"));
        assertTrue(options.findById(o.getId()).isPresent());

        // Indisponibilizar continua permitido
        mvc.perform(post("/admin/opcoes/itens/" + o.getId() + "/disponibilidade").with(user("admin@pratoja.com.br").roles("STORE_ADMIN")).with(csrf()))
                .andExpect(status().is3xxRedirection());
        assertFalse(options.findById(o.getId()).orElseThrow().isAvailable());

        // Grupo com opção referenciada também não pode ser excluído
        mvc.perform(post("/admin/opcoes/grupos/" + g.getId() + "/excluir").with(user("admin@pratoja.com.br").roles("STORE_ADMIN")).with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attributeExists("error"));
        assertTrue(groups.findById(g.getId()).isPresent());
    }
}

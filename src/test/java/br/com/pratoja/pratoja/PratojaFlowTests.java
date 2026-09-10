package br.com.pratoja.pratoja;

import br.com.pratoja.pratoja.domain.DomainTypes;
import br.com.pratoja.pratoja.domain.Address;
import br.com.pratoja.pratoja.domain.Order;
import br.com.pratoja.pratoja.domain.User;
import br.com.pratoja.pratoja.repository.AddressRepository;
import br.com.pratoja.pratoja.repository.OrderRepository;
import br.com.pratoja.pratoja.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.mock.web.MockHttpSession;

import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class PratojaFlowTests {
    @Autowired MockMvc mvc;
    @Autowired UserRepository users;
    @Autowired AddressRepository addresses;
    @Autowired OrderRepository orders;
    @Autowired PasswordEncoder encoder;

    @Test
    void publicCatalogAndSearchAreAvailable() throws Exception {
        mvc.perform(get("/")).andExpect(status().isOk()).andExpect(content().string(containsString("PratoJá")));
        mvc.perform(get("/cardapio").param("q", "frango")).andExpect(status().isOk())
                .andExpect(content().string(containsString("Frango")));
        mvc.perform(get("/produto/1")).andExpect(status().isOk());
        mvc.perform(get("/monte-seu-prato")).andExpect(status().isOk());
    }

    @Test
    void registrationAndLoginAreAvailable() throws Exception {
        String email = "flow-" + UUID.randomUUID() + "@example.com";
        mvc.perform(post("/cadastro").with(csrf())
                        .param("name", "Cliente de Fluxo")
                        .param("email", email)
                        .param("phone", "(11) 98888-7777")
                        .param("password", "Cliente@123")
                        .param("confirmPassword", "Cliente@123"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Cadastro realizado")));

        mvc.perform(post("/login").with(csrf())
                        .param("email", email)
                        .param("password", "Cliente@123"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/cardapio"));
    }

    @Test
    void accountAndRecoveryViewsRender() throws Exception {
        mvc.perform(get("/login")).andExpect(status().isOk())
                .andExpect(content().string(containsString("Entre na sua conta")));
        mvc.perform(get("/cadastro")).andExpect(status().isOk())
                .andExpect(content().string(containsString("Crie sua conta")));
        mvc.perform(get("/recuperar-senha")).andExpect(status().isOk())
                .andExpect(content().string(containsString("Esqueceu a senha")));
    }

    @Test
    void cartCanAddUpdateAndRemoveItems() throws Exception {
        MvcResult added = mvc.perform(post("/api/cart/items").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productId\":1,\"quantity\":2,\"optionIds\":[],\"notes\":\"\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(2))
                .andExpect(jsonPath("$.subtotal").value(65.80))
                .andReturn();

        String body = added.getResponse().getContentAsString();
        String key = body.replaceFirst(".*\"key\":\"([^\"]+)\".*", "$1");
        MockHttpSession session = (MockHttpSession) added.getRequest().getSession(false);
        mvc.perform(patch("/api/cart/items/" + key).with(csrf())
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"quantity\":1}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.count").value(1));
        mvc.perform(delete("/api/cart/items/" + key).with(csrf()).session(session))
                .andExpect(status().isOk()).andExpect(jsonPath("$.count").value(0));
    }

    @Test
    @WithMockUser(username = "cliente@pratoja.com.br", roles = "CUSTOMER")
    void customerCannotAccessAdmin() throws Exception {
        mvc.perform(get("/admin")).andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/?acesso-negado"));
        mvc.perform(get("/admin/clientes")).andExpect(status().is3xxRedirection());
    }

    @Test
    void anonymousIsRedirectedToLoginOnProtectedPages() throws Exception {
        mvc.perform(get("/checkout")).andExpect(status().is3xxRedirection());
        mvc.perform(get("/historico")).andExpect(status().is3xxRedirection());
    }

    @Test
    @WithMockUser(username = "admin@pratoja.com.br", roles = "ADMIN")
    void adminCanAccessDashboard() throws Exception {
        mvc.perform(get("/admin")).andExpect(status().isOk())
                .andExpect(content().string(containsString("Visão geral")));
    }

    @Test
    @WithMockUser(username = "admin@pratoja.com.br", roles = "ADMIN")
    void adminManagementViewsRender() throws Exception {
        mvc.perform(get("/admin/pedidos")).andExpect(status().isOk())
                .andExpect(content().string(containsString("Gestão de pedidos")));
        mvc.perform(get("/admin/produtos")).andExpect(status().isOk())
                .andExpect(content().string(containsString("Produtos e categorias")));
        mvc.perform(get("/admin/produtos/novo")).andExpect(status().isOk())
                .andExpect(content().string(containsString("Novo produto")));
        mvc.perform(get("/admin/relatorios")).andExpect(status().isOk())
                .andExpect(content().string(containsString("Relatório de vendas")));
        mvc.perform(get("/admin/clientes")).andExpect(status().isOk())
                .andExpect(content().string(containsString("Clientes")));
    }

    @Test
    void authenticatedCustomerCanAccessProfile() throws Exception {
        String email = "profile-" + UUID.randomUUID() + "@example.com";
        User user = new User();
        user.setName("Perfil Teste");
        user.setEmail(email);
        user.setPhone("(11) 97777-6666");
        user.setPasswordHash(encoder.encode("Cliente@123"));
        user.setRole(DomainTypes.Role.CUSTOMER);
        users.save(user);

        mvc.perform(get("/perfil").with(user(email).roles("CUSTOMER")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Perfil")));
    }

    @Test
    void customerCanCheckoutAndAdminCanAdvanceOrder() throws Exception {
        User customer = users.findByEmailIgnoreCase("cliente@pratoja.com.br").orElseThrow();
        Address address = new Address();
        address.setUser(customer);
        address.setLabel("Casa de teste");
        address.setZipCode("01001-000");
        address.setStreet("Rua de Demonstração");
        address.setNumber("100");
        address.setNeighborhood("Centro");
        address.setCity("São Paulo");
        address.setState("SP");
        address.setPrimaryAddress(true);
        address = addresses.save(address);

        var customerAuth = user(customer.getEmail()).roles("CUSTOMER");
        MvcResult added = mvc.perform(post("/api/cart/items").with(customerAuth).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productId\":1,\"quantity\":1,\"optionIds\":[],\"notes\":\"Sem cebola\"}"))
                .andExpect(status().isOk()).andReturn();
        MockHttpSession session = (MockHttpSession) added.getRequest().getSession(false);

        mvc.perform(post("/checkout").with(customerAuth).with(csrf()).session(session)
                        .param("fulfillment", "DELIVERY")
                        .param("addressId", address.getId().toString())
                        .param("paymentMethod", "CASH")
                        .param("changeFor", "100,00")
                        .param("notes", "Tocar a campainha"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("/pedidos/*/acompanhar"));

        Order order = orders.findAllByOrderByCreatedAtDesc().get(0);
        mvc.perform(get("/pedidos/" + order.getId() + "/acompanhar").with(customerAuth))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Pedido recebido")))
                .andExpect(content().string(containsString("Saiu para entrega")))
                .andExpect(content().string(containsString("Aguardando")));

        mvc.perform(post("/admin/pedidos/" + order.getId() + "/status")
                        .with(user("admin@pratoja.com.br").roles("ADMIN")).with(csrf())
                        .param("status", "CONFIRMED"))
                .andExpect(status().is3xxRedirection());
    }

    @Test
    void customerCannotReadAnotherCustomersOrder() throws Exception {
        User owner = users.findByEmailIgnoreCase("cliente@pratoja.com.br").orElseThrow();
        Order order = orders.findByUserIdOrderByCreatedAtDesc(owner.getId()).stream().findFirst().orElse(null);
        if (order == null) return;

        String otherEmail = "other-" + UUID.randomUUID() + "@example.com";
        User other = new User();
        other.setName("Outro Cliente");
        other.setEmail(otherEmail);
        other.setPhone("(11) 96666-5555");
        other.setPasswordHash(encoder.encode("Cliente@123"));
        other.setRole(DomainTypes.Role.CUSTOMER);
        users.save(other);

        mvc.perform(get("/pedidos/" + order.getId() + "/acompanhar")
                        .with(user(otherEmail).roles("CUSTOMER")))
                .andExpect(status().isNotFound());
    }

    private static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.UserRequestPostProcessor user(String username) {
        return org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user(username);
    }
}

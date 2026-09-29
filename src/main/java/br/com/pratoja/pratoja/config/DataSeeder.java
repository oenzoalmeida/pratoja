package br.com.pratoja.pratoja.config;

import br.com.pratoja.pratoja.domain.*;
import br.com.pratoja.pratoja.repository.*;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.util.List;

@Component @RequiredArgsConstructor
public class DataSeeder implements CommandLineRunner {
    private static final Logger log = LoggerFactory.getLogger(DataSeeder.class);
    private final UserRepository users; private final CategoryRepository categories; private final ProductRepository products;
    private final OptionGroupRepository groups; private final ProductOptionRepository options; private final PasswordEncoder encoder;
    private final StoreRepository stores;

    @Override @Transactional public void run(String... args) {
        // A senha administrativa vem exclusivamente de PRATOJA_ADMIN_PASSWORD (nenhum padrão embutido).
        // Em produção (Render) a ausência derruba o boot; localmente apenas pula a criação/sincronização do admin.
        final String adminPassword = System.getenv("PRATOJA_ADMIN_PASSWORD");
        final boolean missing = adminPassword == null || adminPassword.isBlank();
        if (missing && "true".equalsIgnoreCase(System.getenv("RENDER")))
            throw new IllegalStateException("PRATOJA_ADMIN_PASSWORD não definida: obrigatória em produção. Configure em Environment do serviço no Render.");
        // Loja migrada pela V6 (backfill da antiga store_settings singleton -> id=1).
        Store loja = stores.findFirstByActiveTrueOrderByIdAsc().orElseGet(this::defaultStore);
        if (missing) log.warn("PRATOJA_ADMIN_PASSWORD ausente: conta admin não criada/sincronizada (obrigatória em produção).");
        else {
            seedUser("Administrador", "admin@pratoja.com.br", "(11) 99999-1000", adminPassword, DomainTypes.Role.STORE_ADMIN, loja);
            users.findByEmailIgnoreCase("admin@pratoja.com.br").ifPresent(admin -> {
                if (admin.getStore() == null) { admin.setStore(loja); users.save(admin); } // migração V6: admin antigo vira STORE_ADMIN da loja 1
                if (!encoder.matches(adminPassword, admin.getPasswordHash())) {
                    admin.setPasswordHash(encoder.encode(adminPassword));
                    users.save(admin);
                }
            });
        }
        // PLATFORM_ADMIN só é criado se a env existir (não quebra o boot sem ela).
        final String platformPassword = System.getenv("PRATOJA_PLATFORM_ADMIN_PASSWORD");
        if (platformPassword != null && !platformPassword.isBlank())
            seedUser("Plataforma", "platform@pratoja.com.br", "(11) 99999-0000", platformPassword, DomainTypes.Role.PLATFORM_ADMIN, null);
        seedUser("Cliente Demonstração", "cliente@pratoja.com.br", "(11) 98888-2000", "Cliente@123", DomainTypes.Role.CUSTOMER, null);
        if (categories.count() > 0) return;
        Category pratos = category(loja, "Pratos executivos", 1); Category lanches = category(loja, "Lanches", 2); Category bebidas = category(loja, "Bebidas", 3); Category monte = category(loja, "Monte seu prato", 0);
        product(pratos, "Frango grelhado da casa", "File de frango suculento, arroz, feijao e salada fresca.", "32.90", "/images/frango.jpg", true, false);
        product(pratos, "Bife acebolado", "Bife macio com cebolas douradas, arroz soltinho, feijao e fritas.", "38.90", "/images/bife.jpg", true, false);
        product(pratos, "Parmegiana artesanal", "Frango empanado, molho de tomate, mucarela, arroz e batatas rusticas.", "41.90", "/images/parmegiana.jpg", false, false);
        product(lanches, "Smash Duplo", "Pao brioche, dois smash burgers, queijo, cebola caramelizada e molho da casa.", "29.90", "/images/smash.jpg", true, false);
        product(bebidas, "Suco natural", "Suco preparado na hora. Consulte os sabores disponiveis.", "9.90", "/images/suco.jpg", false, false);
        Product custom = product(monte, "Monte seu prato", "Escolha cada detalhe e crie uma refeicao do seu jeito.", "15.90", "/images/monte.jpg", true, true);
        addGroup(custom, DomainTypes.OptionType.BASE, "Escolha a base", 1, 1, 1, List.of(new Opt("Arroz branco", "0"), new Opt("Arroz integral", "2"), new Opt("Arroz + feijão", "3")));
        addGroup(custom, DomainTypes.OptionType.PROTEIN, "Escolha a proteína", 1, 1, 2, List.of(new Opt("Frango grelhado", "12"), new Opt("Carne acebolada", "16"), new Opt("Omelete", "8")));
        addGroup(custom, DomainTypes.OptionType.SIDE, "Acompanhamentos", 0, 3, 3, List.of(new Opt("Salada fresca", "4"), new Opt("Legumes salteados", "5"), new Opt("Batata rústica", "7"), new Opt("Farofa crocante", "3")));
        addGroup(custom, DomainTypes.OptionType.EXTRA, "Adicionais", 0, 5, 4, List.of(new Opt("Ovo", "3"), new Opt("Bacon", "5"), new Opt("Queijo", "4"), new Opt("Molho da casa", "2")));
        addGroup(custom, DomainTypes.OptionType.BEVERAGE, "Bebida", 0, 1, 5, List.of(new Opt("Água mineral", "4"), new Opt("Refrigerante lata", "6"), new Opt("Suco natural", "9")));
    }

    /** Fallback defensivo: só usado se a loja migrada pela V6 não existir (nunca deve ocorrer após Flyway). */
    private Store defaultStore() {
        Store s = new Store();
        s.setSlug("restaurante"); s.setName("Restaurante"); s.setSlogan("Seu cardápio digital");
        s.setDeliveryFee(new BigDecimal("6.00")); s.setBrandPrimary("#ef5b2a"); s.setBrandPrimaryDark("#d94717");
        return stores.save(s);
    }

    private void seedUser(String name, String email, String phone, String password, DomainTypes.Role role, Store store) {
        if (users.existsByEmailIgnoreCase(email)) return;
        User u = new User(); u.setName(name); u.setEmail(email); u.setPhone(phone); u.setPasswordHash(encoder.encode(password)); u.setRole(role); u.setStore(store); users.save(u);
    }
    private Category category(Store store, String name, int order) { Category c = new Category(); c.setStore(store); c.setName(name); c.setSortOrder(order); return categories.save(c); }
    private Product product(Category c, String name, String desc, String price, String image, boolean featured, boolean custom) {
        Product p = new Product(); p.setStore(c.getStore()); p.setCategory(c); p.setName(name); p.setDescription(desc); p.setPrice(new BigDecimal(price)); p.setImagePath(image); p.setFeatured(featured); p.setCustomizable(custom); return products.save(p);
    }
    private record Opt(String name, String price) {}
    private void addGroup(Product product, DomainTypes.OptionType type, String name, int min, int max, int sort, List<Opt> values) {
        OptionGroup g = new OptionGroup(); g.setProduct(product); g.setStore(product.getStore()); g.setType(type); g.setName(name); g.setMinSelections(min); g.setMaxSelections(max); g.setSortOrder(sort); groups.save(g);
        int i=0; for (Opt value : values) { ProductOption o = new ProductOption(); o.setGroup(g); o.setName(value.name); o.setPriceDelta(new BigDecimal(value.price)); o.setSortOrder(++i); options.save(o); }
    }
}

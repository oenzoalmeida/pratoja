package br.com.pratoja.pratoja.web;

import br.com.pratoja.pratoja.domain.DomainTypes;
import br.com.pratoja.pratoja.domain.Store;
import br.com.pratoja.pratoja.domain.User;
import br.com.pratoja.pratoja.repository.*;
import br.com.pratoja.pratoja.service.StoreService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.math.BigDecimal;
import java.text.Normalizer;
import java.time.LocalDateTime;
import java.util.*;

/**
 * Fase 5: painel do PLATFORM_ADMIN (/platform). Gestão de lojas (criar, ativar/desativar,
 * editar dados básicos) e de administradores de loja (criar/desativar — nunca deletar).
 * A plataforma NÃO vê dados transacionais sensíveis: apenas métricas agregadas (nº de pedidos/produtos/admins).
 */
@Controller @RequestMapping("/platform") @RequiredArgsConstructor
public class PlatformController {
    private final StoreRepository stores;
    private final ProductRepository products;
    private final OrderRepository orders;
    private final UserRepository users;
    private final StoreService storeService;
    private final org.springframework.security.crypto.password.PasswordEncoder encoder;

    private record StoreRow(Store store, long productCount, long orderCount, long adminCount) {}

    @GetMapping
    String list(Model m) {
        List<StoreRow> rows = stores.findAll().stream()
                .sorted(Comparator.comparing(Store::getId))
                .map(s -> new StoreRow(s, products.countByStore_IdAndArchivedFalse(s.getId()), orders.countByStore_Id(s.getId()),
                        users.findByStore_IdOrderByNameAsc(s.getId()).stream().filter(u -> u.getRole() == DomainTypes.Role.STORE_ADMIN && u.isActive()).count()))
                .toList();
        m.addAttribute("rows", rows);
        return "platform/stores";
    }

    @GetMapping("/lojas/nova")
    String newStore(Model m) { m.addAttribute("store", new Store()); return "platform/store-form"; }

    @PostMapping("/lojas")
    String create(@RequestParam String name, @RequestParam(required = false) String slug, @RequestParam(defaultValue = "0.00") BigDecimal deliveryFee,
                  @RequestParam(required = false) String slogan, RedirectAttributes redirect) {
        try {
            Store s = new Store();
            applySlug(s, slug, name);
            if (name == null || name.strip().length() < 2) throw new IllegalArgumentException("Informe o nome da loja (mínimo de 2 caracteres).");
            s.setName(name.strip());
            if (deliveryFee == null || deliveryFee.signum() < 0) throw new IllegalArgumentException("A taxa de entrega deve ser maior ou igual a zero.");
            s.setDeliveryFee(deliveryFee);
            s.setSlogan(slogan == null || slogan.isBlank() ? null : slogan.strip());
            s.setBrandPrimary("#ef5b2a"); s.setBrandPrimaryDark("#d94717");
            stores.save(s);
            redirect.addFlashAttribute("success", "Loja criada.");
            return "redirect:/platform/lojas/" + s.getId();
        } catch (IllegalArgumentException e) {
            redirect.addFlashAttribute("error", e.getMessage());
            return "redirect:/platform/lojas/nova";
        }
    }

    @PostMapping("/lojas/{id}/status")
    String toggle(@PathVariable Long id, RedirectAttributes redirect) {
        Store s = storeService.byId(id);
        s.setActive(!s.isActive());
        s.setUpdatedAt(LocalDateTime.now());
        stores.save(s);
        redirect.addFlashAttribute("success", s.isActive() ? "Loja ativada." : "Loja desativada (dados preservados).");
        return "redirect:/platform";
    }

    @GetMapping("/lojas/{id}")
    String detail(@PathVariable Long id, Model m) {
        Store s = storeService.byId(id);
        m.addAttribute("store", s);
        m.addAttribute("admins", users.findByStore_IdOrderByNameAsc(s.getId()).stream().filter(u -> u.getRole() == DomainTypes.Role.STORE_ADMIN).toList());
        return "platform/store-detail";
    }

    @PostMapping("/lojas/{id}/editar")
    String edit(@PathVariable Long id, @RequestParam String name, @RequestParam String slug, @RequestParam BigDecimal deliveryFee,
                @RequestParam(required = false) String slogan, @RequestParam(required = false) String description,
                @RequestParam(required = false) String phone, @RequestParam(required = false) String whatsapp, @RequestParam(required = false) String email,
                @RequestParam(required = false) String addressStreet, @RequestParam(required = false) String addressNumber, @RequestParam(required = false) String addressComplement,
                @RequestParam(required = false) String addressNeighborhood, @RequestParam(required = false) String addressCity, @RequestParam(required = false) String addressState,
                @RequestParam(required = false) String addressZip, @RequestParam(required = false) String openingHours,
                @RequestParam(required = false) String heroTitle, @RequestParam(required = false) String heroSubtitle,
                @RequestParam(required = false) String deliveryTimeNote, @RequestParam(required = false) String brandPrimary,
                @RequestParam(required = false) String brandPrimaryDark, @RequestParam(required = false) String logoPath,
                RedirectAttributes redirect) {
        try {
            Store s = storeService.byId(id);
            applySlug(s, slug, name.equals(s.getName()) ? null : name);
            if (name == null || name.strip().length() < 2) throw new IllegalArgumentException("Informe o nome da loja (mínimo de 2 caracteres).");
            if (deliveryFee == null || deliveryFee.signum() < 0) throw new IllegalArgumentException("A taxa de entrega deve ser maior ou igual a zero.");
            s.setName(name.strip()); s.setDeliveryFee(deliveryFee);
            s.setSlogan(bl(slogan)); s.setDescription(bl(description)); s.setPhone(bl(phone)); s.setWhatsapp(bl(whatsapp)); s.setEmail(bl(email));
            s.setAddressStreet(bl(addressStreet)); s.setAddressNumber(bl(addressNumber)); s.setAddressComplement(bl(addressComplement));
            s.setAddressNeighborhood(bl(addressNeighborhood)); s.setAddressCity(bl(addressCity)); s.setAddressState(bl(addressState)); s.setAddressZip(bl(addressZip));
            s.setOpeningHours(openingHours == null ? "" : openingHours.strip()); s.setHeroTitle(bl(heroTitle)); s.setHeroSubtitle(bl(heroSubtitle));
            s.setDeliveryTimeNote(bl(deliveryTimeNote)); s.setBrandPrimary(bl(brandPrimary)); s.setBrandPrimaryDark(bl(brandPrimaryDark)); s.setLogoPath(bl(logoPath));
            s.setUpdatedAt(LocalDateTime.now());
            stores.save(s);
            redirect.addFlashAttribute("success", "Dados da loja salvos.");
            return "redirect:/platform/lojas/" + id;
        } catch (IllegalArgumentException e) {
            redirect.addFlashAttribute("error", e.getMessage());
            return "redirect:/platform/lojas/" + id;
        }
    }

    @PostMapping("/lojas/{id}/admins")
    String createAdmin(@PathVariable Long id, @RequestParam String name, @RequestParam String email, @RequestParam String password, RedirectAttributes redirect) {
        try {
            if (name == null || name.isBlank()) throw new IllegalArgumentException("Informe o nome do administrador.");
            String mail = email == null ? "" : email.strip().toLowerCase();
            if (!mail.matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")) throw new IllegalArgumentException("Informe um e-mail válido.");
            if (password == null || password.length() < 8) throw new IllegalArgumentException("A senha inicial deve ter ao menos 8 caracteres.");
            if (users.existsByEmailIgnoreCase(mail)) throw new IllegalArgumentException("Já existe um usuário com este e-mail.");
            User u = new User();
            u.setName(name.strip()); u.setEmail(mail); u.setPasswordHash(encoder.encode(password));
            u.setRole(DomainTypes.Role.STORE_ADMIN); u.setStore(storeService.byId(id));
            users.save(u);
            redirect.addFlashAttribute("success", "Administrador criado.");
        } catch (IllegalArgumentException e) {
            redirect.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/platform/lojas/" + id;
    }

    @PostMapping("/admins/{id}/desativar")
    String deactivateAdmin(@PathVariable Long id, RedirectAttributes redirect) {
        User u = users.findById(id).orElseThrow();
        if (u.getRole() == DomainTypes.Role.STORE_ADMIN) { u.setActive(false); users.save(u); redirect.addFlashAttribute("success", "Administrador desativado."); }
        return "redirect:/platform/lojas/" + (u.getStore() != null ? u.getStore().getId() : "");
    }

    /** Slug gerado a partir do nome, editável; validação de unicidade. */
    private void applySlug(Store s, String requested, String fallbackName) {
        String base = requested != null && !requested.isBlank() ? requested : slugify(fallbackName);
        String candidate = slugify(base);
        if (candidate.isBlank()) throw new IllegalArgumentException("Informe um slug válido (letras, números e hífen).");
        if (s.getId() == null || !candidate.equals(s.getSlug())) {
            if (stores.existsBySlug(candidate)) throw new IllegalArgumentException("O slug “" + candidate + "” já está em uso por outra loja.");
        }
        s.setSlug(candidate);
    }

    static String slugify(String value) {
        if (value == null) return "";
        String norm = Normalizer.normalize(value.strip().toLowerCase(), Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        String slug = norm.replaceAll("[^a-z0-9]+", "-").replaceAll("^-+|-+$", "");
        return slug.length() > 80 ? slug.substring(0, 80).replaceAll("-+$", "") : slug;
    }

    private static String bl(String v) { if (v == null) return null; String x = v.strip(); return x.isEmpty() ? null : x; }
}

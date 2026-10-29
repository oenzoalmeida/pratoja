package br.com.pratoja.pratoja.web;

import br.com.pratoja.pratoja.domain.*;
import br.com.pratoja.pratoja.repository.*;
import br.com.pratoja.pratoja.service.StoreService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.math.BigDecimal;
import java.util.*;

/**
 * Painel do restaurante para os grupos e opções de personalização ("Monte seu prato").
 * Todo acesso é escopado pela loja do admin logado: grupo/opção de outra loja -> 404 (SecurityException).
 * Opção usada em pedidos anteriores não pode ser excluída (histórico preservado); o painel oferece
 * indisponibilizar, que a remove das escolhas novas sem tocar no histórico.
 */
@Controller @RequestMapping("/admin/opcoes") @RequiredArgsConstructor
public class AdminOptionsController {
    private final StoreService storeService;
    private final ProductRepository products;
    private final OptionGroupRepository groups;
    private final ProductOptionRepository options;
    private final OrderItemOptionRepository orderItemOptions;

    private Store store(Authentication auth) { return storeService.adminStore(auth); }

    public record GroupView(OptionGroup group, List<ProductOption> items) {}
    public record ProductView(Product product, List<GroupView> groupViews) {}

    @GetMapping
    String page(Authentication auth, Model model) {
        Store s = store(auth);
        List<Product> customizable = products.findByStore_IdAndCustomizableTrueAndArchivedFalseOrderByNameAsc(s.getId());
        List<ProductView> views = new ArrayList<>();
        for (Product p : customizable) {
            List<GroupView> gvs = groups.findByProductIdOrderBySortOrder(p.getId()).stream()
                    .map(g -> new GroupView(g, options.findByGroupIdOrderBySortOrder(g.getId()))).toList();
            views.add(new ProductView(p, gvs));
        }
        model.addAttribute("productViews", views);
        model.addAttribute("types", DomainTypes.OptionType.values());
        return "admin/options";
    }

    @PostMapping("/grupos")
    String createGroup(Authentication auth, @RequestParam Long productId, @RequestParam String type,
                       @RequestParam String name, @RequestParam int minSelections, @RequestParam int maxSelections,
                       @RequestParam(defaultValue = "0") int sortOrder, RedirectAttributes redirect) {
        try {
            Store s = store(auth);
            Product p = products.findByIdAndStore_Id(productId, s.getId())
                    .filter(x -> x.isCustomizable() && !x.isArchived()).orElseThrow(SecurityException::new);
            OptionGroup g = new OptionGroup();
            applyGroup(g, type, name, minSelections, maxSelections, sortOrder);
            g.setProduct(p);
            g.setStore(s);
            groups.save(g);
            redirect.addFlashAttribute("success", "Grupo criado.");
        } catch (IllegalArgumentException e) {
            redirect.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/admin/opcoes";
    }

    @PostMapping("/grupos/{id}/editar")
    String editGroup(Authentication auth, @PathVariable Long id, @RequestParam String type,
                     @RequestParam String name, @RequestParam int minSelections, @RequestParam int maxSelections,
                     @RequestParam(defaultValue = "0") int sortOrder, RedirectAttributes redirect) {
        try {
            Store s = store(auth);
            OptionGroup g = groups.findByIdAndStore_Id(id, s.getId()).orElseThrow(SecurityException::new);
            applyGroup(g, type, name, minSelections, maxSelections, sortOrder);
            groups.save(g);
            redirect.addFlashAttribute("success", "Grupo atualizado.");
        } catch (IllegalArgumentException e) {
            redirect.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/admin/opcoes";
    }

    @PostMapping("/grupos/{id}/excluir")
    String deleteGroup(Authentication auth, @PathVariable Long id, RedirectAttributes redirect) {
        Store s = store(auth);
        OptionGroup g = groups.findByIdAndStore_Id(id, s.getId()).orElseThrow(SecurityException::new);
        List<ProductOption> items = options.findByGroupIdOrderBySortOrder(g.getId());
        boolean referenced = items.stream().anyMatch(o -> orderItemOptions.existsByOption_Id(o.getId()));
        if (referenced) {
            redirect.addFlashAttribute("error", "Este grupo tem opções usadas em pedidos anteriores. Indisponibilize as opções em vez de excluir, para preservar o histórico.");
            return "redirect:/admin/opcoes";
        }
        options.deleteAll(items);
        groups.delete(g);
        redirect.addFlashAttribute("success", "Grupo excluído.");
        return "redirect:/admin/opcoes";
    }

    @PostMapping("/grupos/{id}/itens")
    String createOption(Authentication auth, @PathVariable Long id, @RequestParam String name,
                        @RequestParam BigDecimal priceDelta, @RequestParam(defaultValue = "0") int sortOrder,
                        RedirectAttributes redirect) {
        try {
            Store s = store(auth);
            OptionGroup g = groups.findByIdAndStore_Id(id, s.getId()).orElseThrow(SecurityException::new);
            ProductOption o = new ProductOption();
            applyOption(o, name, priceDelta, sortOrder);
            o.setGroup(g);
            options.save(o);
            redirect.addFlashAttribute("success", "Opção adicionada.");
        } catch (IllegalArgumentException e) {
            redirect.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/admin/opcoes";
    }

    @PostMapping("/itens/{id}/editar")
    String editOption(Authentication auth, @PathVariable Long id, @RequestParam String name,
                      @RequestParam BigDecimal priceDelta, @RequestParam(defaultValue = "0") int sortOrder,
                      RedirectAttributes redirect) {
        try {
            Store s = store(auth);
            ProductOption o = options.findByIdAndGroup_Store_Id(id, s.getId()).orElseThrow(SecurityException::new);
            applyOption(o, name, priceDelta, sortOrder);
            options.save(o);
            redirect.addFlashAttribute("success", "Opção atualizada.");
        } catch (IllegalArgumentException e) {
            redirect.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/admin/opcoes";
    }

    @PostMapping("/itens/{id}/disponibilidade")
    String toggleOption(Authentication auth, @PathVariable Long id, RedirectAttributes redirect) {
        Store s = store(auth);
        ProductOption o = options.findByIdAndGroup_Store_Id(id, s.getId()).orElseThrow(SecurityException::new);
        o.setAvailable(!o.isAvailable());
        options.save(o);
        redirect.addFlashAttribute("success", o.isAvailable() ? "Opção disponível." : "Opção indisponibilizada.");
        return "redirect:/admin/opcoes";
    }

    @PostMapping("/itens/{id}/excluir")
    String deleteOption(Authentication auth, @PathVariable Long id, RedirectAttributes redirect) {
        Store s = store(auth);
        ProductOption o = options.findByIdAndGroup_Store_Id(id, s.getId()).orElseThrow(SecurityException::new);
        if (orderItemOptions.existsByOption_Id(o.getId())) {
            redirect.addFlashAttribute("error", "Esta opção já foi usada em pedidos. Indisponibilize-a em vez de excluir, para preservar o histórico.");
            return "redirect:/admin/opcoes";
        }
        options.delete(o);
        redirect.addFlashAttribute("success", "Opção excluída.");
        return "redirect:/admin/opcoes";
    }

    private void applyGroup(OptionGroup g, String type, String name, int min, int max, int sortOrder) {
        DomainTypes.OptionType t;
        try { t = DomainTypes.OptionType.valueOf(type == null ? "" : type); }
        catch (Exception e) { throw new IllegalArgumentException("Tipo de grupo inválido."); }
        if (name == null || name.strip().length() < 2 || name.strip().length() > 80)
            throw new IllegalArgumentException("Informe o nome do grupo (2 a 80 caracteres).");
        if (min < 0 || max < 1 || min > max || max > 50)
            throw new IllegalArgumentException("Revise o mínimo e o máximo de escolhas (0 ≤ mínimo ≤ máximo, máximo entre 1 e 50).");
        g.setType(t);
        g.setName(name.strip());
        g.setMinSelections(min);
        g.setMaxSelections(max);
        g.setSortOrder(sortOrder);
    }

    private void applyOption(ProductOption o, String name, BigDecimal priceDelta, int sortOrder) {
        if (name == null || name.strip().length() < 2 || name.strip().length() > 100)
            throw new IllegalArgumentException("Informe o nome da opção (2 a 100 caracteres).");
        if (priceDelta == null || priceDelta.signum() < 0 || priceDelta.compareTo(new BigDecimal("9999.99")) > 0)
            throw new IllegalArgumentException("O acréscimo de preço deve ser entre 0 e 9999,99.");
        o.setName(name.strip());
        o.setPriceDelta(priceDelta);
        o.setSortOrder(sortOrder);
    }
}

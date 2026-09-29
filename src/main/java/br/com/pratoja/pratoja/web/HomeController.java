package br.com.pratoja.pratoja.web;

import br.com.pratoja.pratoja.domain.*;
import br.com.pratoja.pratoja.repository.*;
import br.com.pratoja.pratoja.service.StoreService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.view.RedirectView;

import java.util.*;

/**
 * Fase 4: navegação pública da loja sob /loja/{slug}. A loja é resolvida UMA vez por handler
 * (StoreService.activeBySlug: exige active=true; inativa/inesistente -> 404 "loja indisponível").
 * Rotas antigas sem slug (/, /cardapio, ...) redirecionam 301 para a loja única ativa;
 * com mais de uma loja ativa, / vira landing da plataforma listando as lojas.
 */
@Controller @RequiredArgsConstructor
public class HomeController {
    private final ProductRepository products; private final CategoryRepository categories;
    private final OptionGroupRepository groups; private final ProductOptionRepository options;
    private final StoreService stores;

    /** Redirecionamento permanente (301) para a loja única ativa (compat de rotas antigas). */
    private RedirectView toStore(String suffix) {
        Store s = stores.singleActive();
        RedirectView view = new RedirectView("/loja/" + s.getSlug() + suffix, true, false);
        view.setStatusCode(HttpStatus.MOVED_PERMANENTLY);
        return view;
    }

    private boolean singleActive() { return stores.countActive() == 1; }

    @GetMapping("/")
    public Object landing(Model model) {
        if (singleActive()) return toStore("");
        model.addAttribute("stores", stores.activeStores());
        return "landing";
    }

    // ---- Rotas antigas (compat 301) ----
    @GetMapping("/cardapio") RedirectView oldMenu() { return toStore("/cardapio"); }
    @GetMapping("/produto/{id}") RedirectView oldProduct(@PathVariable Long id) { return toStore("/produto/" + id); }
    @GetMapping("/monte-seu-prato") RedirectView oldCustom() { return toStore("/monte-seu-prato"); }
    @GetMapping("/carrinho") RedirectView oldCart() { return toStore("/carrinho"); }

    // ---- Rotas por loja (/loja/{slug}) ----
    @GetMapping("/loja/{slug}") String home(@PathVariable String slug, Model model) {
        Store s = stores.activeBySlug(slug);
        model.addAttribute("featured", products.findByStore_IdAndFeaturedTrueAndAvailableTrueAndArchivedFalseOrderByIdDesc(s.getId()));
        return "index";
    }
    @GetMapping("/loja/{slug}/cardapio") String menu(@PathVariable String slug, @RequestParam(required=false) String q, @RequestParam(required=false) Long categoria, Model model) {
        Store s = stores.activeBySlug(slug);
        model.addAttribute("products", products.searchInStore(s.getId(), q == null || q.isBlank() ? null : q.trim(), categoria));
        model.addAttribute("categories", categories.findByStore_IdAndActiveTrueOrderBySortOrderAscNameAsc(s.getId()));
        model.addAttribute("q", q); model.addAttribute("selectedCategory", categoria);
        return "menu";
    }
    @GetMapping("/loja/{slug}/produto/{id}") String product(@PathVariable String slug, @PathVariable Long id, Model model) {
        Store s = stores.activeBySlug(slug);
        Product product = products.findByIdAndStore_Id(id, s.getId()).filter(p -> !p.isArchived()).orElseThrow(NoSuchElementException::new);
        model.addAttribute("product", product); model.addAttribute("groups", optionData(product));
        return "product";
    }
    @GetMapping("/loja/{slug}/monte-seu-prato") String custom(@PathVariable String slug, Model model) {
        Store s = stores.activeBySlug(slug);
        Product product = products.findFirstByStore_IdAndCustomizableTrueAndArchivedFalse(s.getId()).orElseThrow(NoSuchElementException::new);
        model.addAttribute("product", product); model.addAttribute("groups", optionData(product));
        return "custom";
    }
    private List<GroupView> optionData(Product p) {
        return groups.findByProductIdOrderBySortOrder(p.getId()).stream().map(g -> new GroupView(g, options.findByGroupIdOrderBySortOrder(g.getId()))).toList();
    }
    public record GroupView(OptionGroup group, List<ProductOption> options) {}
}

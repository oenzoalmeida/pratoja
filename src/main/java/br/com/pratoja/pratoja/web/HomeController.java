package br.com.pratoja.pratoja.web;

import br.com.pratoja.pratoja.domain.*;
import br.com.pratoja.pratoja.repository.*;
import br.com.pratoja.pratoja.service.StoreService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@Controller @RequiredArgsConstructor
public class HomeController {
    private final ProductRepository products; private final CategoryRepository categories;
    private final OptionGroupRepository groups; private final ProductOptionRepository options;
    private final StoreService stores;

    // Contexto padrão = loja única ativa. Fase 4: resolver pela rota /loja/{slug} (ponto de extensão).
    private Store context() { return stores.singleActive(); }

    @GetMapping("/") String home(Model model) {
        Store s = context();
        model.addAttribute("featured", products.findByStore_IdAndFeaturedTrueAndAvailableTrueAndArchivedFalseOrderByIdDesc(s.getId()));
        return "index";
    }
    @GetMapping("/cardapio") String menu(@RequestParam(required=false) String q, @RequestParam(required=false) Long categoria, Model model) {
        Store s = context();
        model.addAttribute("products", products.searchInStore(s.getId(), q == null || q.isBlank() ? null : q.trim(), categoria));
        model.addAttribute("categories", categories.findByStore_IdAndActiveTrueOrderBySortOrderAscNameAsc(s.getId()));
        model.addAttribute("q", q); model.addAttribute("selectedCategory", categoria);
        return "menu";
    }
    @GetMapping("/produto/{id}") String product(@PathVariable Long id, Model model) {
        Store s = context();
        Product product = products.findByIdAndStore_Id(id, s.getId()).filter(p -> !p.isArchived()).orElseThrow(NoSuchElementException::new);
        model.addAttribute("product", product); model.addAttribute("groups", optionData(product));
        return "product";
    }
    @GetMapping("/monte-seu-prato") String custom(Model model) {
        Store s = context();
        Product product = products.findFirstByStore_IdAndCustomizableTrueAndArchivedFalse(s.getId()).orElseThrow(NoSuchElementException::new);
        model.addAttribute("product", product); model.addAttribute("groups", optionData(product));
        return "custom";
    }
    private List<GroupView> optionData(Product p) {
        return groups.findByProductIdOrderBySortOrder(p.getId()).stream().map(g -> new GroupView(g, options.findByGroupIdOrderBySortOrder(g.getId()))).toList();
    }
    public record GroupView(OptionGroup group, List<ProductOption> options) {}
}

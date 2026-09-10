package br.com.pratoja.pratoja.web;

import br.com.pratoja.pratoja.domain.*;
import br.com.pratoja.pratoja.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@Controller @RequiredArgsConstructor
public class HomeController {
    private final ProductRepository products; private final CategoryRepository categories;
    private final OptionGroupRepository groups; private final ProductOptionRepository options;

    @GetMapping("/") String home(Model model) {
        model.addAttribute("featured", products.findByFeaturedTrueAndAvailableTrueAndArchivedFalseOrderByIdDesc());
        return "index";
    }
    @GetMapping("/cardapio") String menu(@RequestParam(required=false) String q, @RequestParam(required=false) Long categoria, Model model) {
        model.addAttribute("products", products.search(q == null || q.isBlank() ? null : q.trim(), categoria));
        model.addAttribute("categories", categories.findByActiveTrueOrderBySortOrderAscNameAsc());
        model.addAttribute("q", q); model.addAttribute("selectedCategory", categoria);
        return "menu";
    }
    @GetMapping("/produto/{id}") String product(@PathVariable Long id, Model model) {
        Product product = products.findById(id).filter(p -> !p.isArchived()).orElseThrow(NoSuchElementException::new);
        model.addAttribute("product", product); model.addAttribute("groups", optionData(product));
        return "product";
    }
    @GetMapping("/monte-seu-prato") String custom(Model model) {
        Product product = products.findFirstByCustomizableTrueAndArchivedFalse().orElseThrow(NoSuchElementException::new);
        model.addAttribute("product", product); model.addAttribute("groups", optionData(product));
        return "custom";
    }
    private List<GroupView> optionData(Product p) {
        return groups.findByProductIdOrderBySortOrder(p.getId()).stream().map(g -> new GroupView(g, options.findByGroupIdOrderBySortOrder(g.getId()))).toList();
    }
    public record GroupView(OptionGroup group, List<ProductOption> options) {}
}

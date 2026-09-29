package br.com.pratoja.pratoja.web;

import br.com.pratoja.pratoja.domain.Store;
import br.com.pratoja.pratoja.service.CurrentUserService;
import br.com.pratoja.pratoja.service.StoreService;
import br.com.pratoja.pratoja.repository.StoreRepository;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

/**
 * Expõe a loja como atributo global "store" em todos os templates Thymeleaf.
 * - Em /admin/**: a loja do admin logado (STORE_ADMIN). PLATFORM_ADMIN cai no fallback.
 * - Em /loja/{slug}/**: a loja resolvida pela rota (active=true; inativa -> null, handlers devolvem 404).
 * - Demais páginas: fallback = loja única ativa (compat).
 */
@ControllerAdvice @RequiredArgsConstructor
public class StoreAdvice {
    private final StoreService storeService;
    private final CurrentUserService currentUser;
    private final StoreRepository stores;

    @ModelAttribute("store") public Store store(HttpServletRequest request, org.springframework.security.core.Authentication auth) {
        if (request == null) return stores.findFirstByActiveTrueOrderByIdAsc().orElse(null);
        String uri = request.getRequestURI();
        // Fase 4: loja em contexto vem da rota /loja/{slug} (uma resolução por request).
        if (uri.startsWith("/loja/")) {
            String[] parts = uri.split("/");
            if (parts.length > 2) return stores.findBySlug(parts[2]).filter(Store::isActive).orElse(null);
            return null;
        }
        if (auth != null && uri.startsWith("/admin")) {
            try {
                var user = currentUser.require(auth);
                if (user.getStore() != null) {
                    Long sid = user.getStore().getId(); // id acessível em proxy lazy
                    return stores.findById(sid).orElse(null);
                }
            } catch (IllegalStateException | NullPointerException ignored) { /* fallback abaixo */ }
        }
        return stores.findFirstByActiveTrueOrderByIdAsc().orElse(null);
    }
}

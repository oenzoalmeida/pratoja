package br.com.pratoja.pratoja.service;

import br.com.pratoja.pratoja.domain.DomainTypes;
import br.com.pratoja.pratoja.domain.Store;
import br.com.pratoja.pratoja.domain.User;
import br.com.pratoja.pratoja.repository.StoreRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Resolução de contexto de loja (tenant) durante as Fases 1-3.
 * Fase 4 introduzirá rotas públicas por slug (/loja/{slug}); até lá as páginas públicas
 * e o checkout usam a loja única ativa como contexto padrão — este método é o ponto de extensão.
 */
@Service @RequiredArgsConstructor
public class StoreService {
    private final StoreRepository stores;
    private final CurrentUserService currentUser;

    /** Loja única ativa (contexto padrão enquanto não há rota por slug — ver comentário de classe). */
    @Transactional(readOnly = true)
    public Store singleActive() {
        return stores.findFirstByActiveTrueOrderByIdAsc().orElseThrow();
    }

    /** Loja do admin logado; PLATFORM_ADMIN não tem loja própria (SecurityException -> 404). */
    @Transactional(readOnly = true)
    public Store adminStore(User admin) {
        if (admin.getRole() == DomainTypes.Role.PLATFORM_ADMIN) throw new SecurityException();
        if (admin.getRole() != DomainTypes.Role.STORE_ADMIN || admin.getStore() == null) throw new SecurityException();
        // Recarrega para evitar proxy lazy fora da sessão (open-in-view=false)
        return stores.findById(admin.getStore().getId()).orElseThrow(SecurityException::new);
    }

    /** Loja do admin autenticado na requisição (404 para quem não tem loja). */
    @Transactional(readOnly = true)
    public Store adminStore(org.springframework.security.core.Authentication auth) {
        return adminStore(currentUser.require(auth));
    }

    @Transactional(readOnly = true) public Store byId(Long id) { return stores.findById(id).orElseThrow(); }
}

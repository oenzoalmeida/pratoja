package br.com.pratoja.pratoja.service;

import br.com.pratoja.pratoja.domain.DomainTypes;
import br.com.pratoja.pratoja.domain.Store;
import br.com.pratoja.pratoja.domain.User;
import br.com.pratoja.pratoja.repository.StoreRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.NoSuchElementException;

/**
 * Resolução de contexto de loja (tenant).
 * Fase 4: as páginas públicas vivem sob /loja/{slug} e resolvem a loja pela rota (active=true obrigatório;
 * loja inativa -> 404). O fallback de loja única ativa permanece apenas para compatibilidade/redirecionamentos.
 */
@Service @RequiredArgsConstructor
public class StoreService {
    private final StoreRepository stores;
    private final br.com.pratoja.pratoja.repository.UserRepository users;
    private final CurrentUserService currentUser;

    /** Loja ativa pelo slug da rota /loja/{slug}; inexistente ou inativa -> 404. */
    @Transactional(readOnly = true)
    public Store activeBySlug(String slug) {
        return stores.findBySlug(slug).filter(Store::isActive).orElseThrow(NoSuchElementException::new);
    }

    /** Loja única ativa — usado só para redirecionar rotas antigas (301) quando existe exatamente 1 loja ativa. */
    @Transactional(readOnly = true)
    public Store singleActive() {
        return stores.findFirstByActiveTrueOrderByIdAsc().orElseThrow();
    }

    @Transactional(readOnly = true)
    public List<Store> activeStores() { return stores.findAll().stream().filter(Store::isActive).toList(); }

    @Transactional(readOnly = true)
    public long countActive() { return activeStores().size(); }

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

    /**
     * Integridade STORE_ADMIN (regra da migration V8): no máximo 1 STORE_ADMIN por loja.
     * Reforçada aqui em service (vale para H2, onde não há índice parcial) e, no PostgreSQL,
     * também pelo unique index parcial users(store_id) WHERE role='STORE_ADMIN'.
     */
    @Transactional(readOnly = true)
    public void assertStoreAdminSlotFree(Long storeId) {
        if (users.existsByRoleAndStore_Id(DomainTypes.Role.STORE_ADMIN, storeId))
            throw new IllegalArgumentException("Esta loja já possui um administrador. Desative o atual antes de criar outro.");
    }
}

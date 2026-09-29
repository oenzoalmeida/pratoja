package br.com.pratoja.pratoja.repository;

import br.com.pratoja.pratoja.domain.Store;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface StoreRepository extends JpaRepository<Store, Long> {
    Optional<Store> findBySlug(String slug);
    boolean existsBySlug(String slug);
    Optional<Store> findFirstByActiveTrueOrderByIdAsc();
}

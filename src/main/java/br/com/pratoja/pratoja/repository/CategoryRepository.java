package br.com.pratoja.pratoja.repository;
import br.com.pratoja.pratoja.domain.Category;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;
public interface CategoryRepository extends JpaRepository<Category, Long> {
    // Escopado por loja (multitenant): sempre filtrar por store_id.
    List<Category> findByStore_IdOrderBySortOrderAscNameAsc(Long storeId);
    List<Category> findByStore_IdAndActiveTrueOrderBySortOrderAscNameAsc(Long storeId);
    Optional<Category> findByIdAndStore_Id(Long id, Long storeId);
    Optional<Category> findByNameIgnoreCaseAndStore_Id(String name, Long storeId);
}

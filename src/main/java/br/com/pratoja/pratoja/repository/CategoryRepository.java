package br.com.pratoja.pratoja.repository;
import br.com.pratoja.pratoja.domain.Category;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;
public interface CategoryRepository extends JpaRepository<Category, Long> {
    List<Category> findByActiveTrueOrderBySortOrderAscNameAsc();
    Optional<Category> findByNameIgnoreCase(String name);
}

package br.com.pratoja.pratoja.repository;
import br.com.pratoja.pratoja.domain.Product;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.util.*;
public interface ProductRepository extends JpaRepository<Product, Long> {
    @Override @EntityGraph(attributePaths="category") Optional<Product> findById(Long id);
    @EntityGraph(attributePaths="category") List<Product> findByArchivedFalseOrderByCategorySortOrderAscNameAsc();
    @EntityGraph(attributePaths="category") List<Product> findByFeaturedTrueAndAvailableTrueAndArchivedFalseOrderByIdDesc();
    Optional<Product> findFirstByCustomizableTrueAndArchivedFalse();
    @EntityGraph(attributePaths="category") @Query("select p from Product p where p.archived=false and (:category is null or p.category.id=:category) and (:q is null or lower(p.name) like lower(concat('%',:q,'%')) or lower(p.description) like lower(concat('%',:q,'%'))) order by p.category.sortOrder,p.name")
    List<Product> search(@Param("q") String q, @Param("category") Long category);
}

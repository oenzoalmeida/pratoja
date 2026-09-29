package br.com.pratoja.pratoja.repository;
import br.com.pratoja.pratoja.domain.Product;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.util.*;
public interface ProductRepository extends JpaRepository<Product, Long> {
    @Override @EntityGraph(attributePaths={"category","store"}) Optional<Product> findById(Long id);
    // Escopado por loja (multitenant)
    @EntityGraph(attributePaths={"category","store"}) Optional<Product> findByIdAndStore_Id(Long id, Long storeId);
    @EntityGraph(attributePaths={"category","store"}) List<Product> findByStore_IdAndArchivedFalseOrderByCategorySortOrderAscNameAsc(Long storeId);
    @EntityGraph(attributePaths={"category","store"}) List<Product> findByStore_IdAndFeaturedTrueAndAvailableTrueAndArchivedFalseOrderByIdDesc(Long storeId);
    Optional<Product> findFirstByStore_IdAndCustomizableTrueAndArchivedFalse(Long storeId);
    @EntityGraph(attributePaths={"category","store"}) @Query("select p from Product p where p.store.id=:store and p.archived=false and (:category is null or p.category.id=:category) and (:q is null or lower(p.name) like lower(concat('%',:q,'%')) or lower(p.description) like lower(concat('%',:q,'%'))) order by p.category.sortOrder,p.name")
    List<Product> searchInStore(@Param("store") Long store, @Param("q") String q, @Param("category") Long category);
}

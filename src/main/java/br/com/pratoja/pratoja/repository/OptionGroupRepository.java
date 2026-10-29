package br.com.pratoja.pratoja.repository;
import br.com.pratoja.pratoja.domain.OptionGroup;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;
public interface OptionGroupRepository extends JpaRepository<OptionGroup, Long> {
    List<OptionGroup> findByProductIdOrderBySortOrder(Long productId);
    // Escopo administrativo por loja (multitenant)
    List<OptionGroup> findByStore_IdOrderBySortOrder(Long storeId);
    Optional<OptionGroup> findByIdAndStore_Id(Long id, Long storeId);
}

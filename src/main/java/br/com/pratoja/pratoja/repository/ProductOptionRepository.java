package br.com.pratoja.pratoja.repository;
import br.com.pratoja.pratoja.domain.ProductOption;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
public interface ProductOptionRepository extends JpaRepository<ProductOption, Long> {
    List<ProductOption> findByGroupIdOrderBySortOrder(Long groupId);
    List<ProductOption> findByIdInAndAvailableTrue(List<Long> ids);
}

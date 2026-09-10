package br.com.pratoja.pratoja.repository;
import br.com.pratoja.pratoja.domain.OptionGroup;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
public interface OptionGroupRepository extends JpaRepository<OptionGroup, Long> {
    List<OptionGroup> findByProductIdOrderBySortOrder(Long productId);
}

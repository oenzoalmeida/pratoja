package br.com.pratoja.pratoja.repository;
import br.com.pratoja.pratoja.domain.OrderItemOption;
import org.springframework.data.jpa.repository.JpaRepository;
public interface OrderItemOptionRepository extends JpaRepository<OrderItemOption, Long> {
    // Uma opcao referenciada por pedidos anteriores nao pode ser excluida (FK sem cascade);
    // o painel orienta indisponibilizar em vez de excluir.
    boolean existsByOption_Id(Long optionId);
}

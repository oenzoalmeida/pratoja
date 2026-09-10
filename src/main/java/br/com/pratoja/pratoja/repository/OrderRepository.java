package br.com.pratoja.pratoja.repository;
import br.com.pratoja.pratoja.domain.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.time.LocalDateTime;
import java.util.*;
public interface OrderRepository extends JpaRepository<Order, Long> {
    @Query("select o from Order o where o.id=:id") Optional<Order> findDetailedById(@Param("id") Long id);
    List<Order> findByUserIdOrderByCreatedAtDesc(Long userId);
    List<Order> findAllByOrderByCreatedAtDesc();
    long countByCreatedAtBetween(LocalDateTime start, LocalDateTime end);
    long countByStatus(DomainTypes.OrderStatus status);
    @Query("select coalesce(sum(o.total),0) from Order o where o.status='DELIVERED' and o.createdAt between :start and :end")
    java.math.BigDecimal revenue(@Param("start") LocalDateTime start,@Param("end") LocalDateTime end);
}

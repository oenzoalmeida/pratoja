package br.com.pratoja.pratoja.repository;
import br.com.pratoja.pratoja.domain.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.time.LocalDateTime;
import java.util.*;
public interface OrderRepository extends JpaRepository<Order, Long> {
    @Query("select o from Order o where o.id=:id") Optional<Order> findDetailedById(@Param("id") Long id);
    List<Order> findByUserIdOrderByCreatedAtDesc(Long userId);
    // Escopado por loja (multitenant)
    List<Order> findAllByStore_IdOrderByCreatedAtDesc(Long storeId);
    Optional<Order> findByIdAndStore_Id(Long id, Long storeId);
    long countByStore_IdAndCreatedAtBetween(Long storeId, LocalDateTime start, LocalDateTime end);
    long countByStore_IdAndStatus(Long storeId, DomainTypes.OrderStatus status);
    @Query("select coalesce(sum(o.total),0) from Order o where o.store.id=:store and o.status='DELIVERED' and o.createdAt between :start and :end")
    java.math.BigDecimal revenueInStore(@Param("store") Long store, @Param("start") LocalDateTime start, @Param("end") LocalDateTime end);
}

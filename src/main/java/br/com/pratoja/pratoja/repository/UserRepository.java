package br.com.pratoja.pratoja.repository;
import br.com.pratoja.pratoja.domain.DomainTypes;
import br.com.pratoja.pratoja.domain.User;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.Optional;
public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByEmailIgnoreCase(String email);
    boolean existsByEmailIgnoreCase(String email);
    List<User> findByRoleOrderByNameAsc(DomainTypes.Role role);
    @Query("select distinct u from User u join Order o on o.user = u where u.role = :role and o.store.id = :storeId order by u.name asc")
    List<User> findCustomersWithOrdersInStore(@Param("role") DomainTypes.Role role, @Param("storeId") Long storeId);
    List<User> findByStore_IdOrderByNameAsc(Long storeId);
    boolean existsByRoleAndStore_Id(DomainTypes.Role role, Long storeId);
}

package br.com.pratoja.pratoja.repository;
import br.com.pratoja.pratoja.domain.DomainTypes;
import br.com.pratoja.pratoja.domain.User;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;
public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByEmailIgnoreCase(String email);
    boolean existsByEmailIgnoreCase(String email);
    List<User> findByRoleOrderByNameAsc(DomainTypes.Role role);
}

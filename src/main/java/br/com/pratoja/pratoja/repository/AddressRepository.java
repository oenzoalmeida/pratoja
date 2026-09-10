package br.com.pratoja.pratoja.repository;
import br.com.pratoja.pratoja.domain.Address;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
public interface AddressRepository extends JpaRepository<Address, Long> {
    List<Address> findByUserIdOrderByPrimaryAddressDescIdDesc(Long userId);
}

package br.com.pratoja.pratoja.repository;

import br.com.pratoja.pratoja.domain.StorePaymentAccount;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface StorePaymentAccountRepository extends JpaRepository<StorePaymentAccount, Long> {
    Optional<StorePaymentAccount> findByStore_Id(Long storeId);
}

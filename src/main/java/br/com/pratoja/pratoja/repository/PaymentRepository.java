package br.com.pratoja.pratoja.repository;

import br.com.pratoja.pratoja.domain.Payment;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface PaymentRepository extends JpaRepository<Payment, Long> {
    Optional<Payment> findByReference(String reference);
    Optional<Payment> findByMpPaymentId(String mpPaymentId);
}

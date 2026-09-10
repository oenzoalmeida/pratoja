package br.com.pratoja.pratoja.repository;
import br.com.pratoja.pratoja.domain.Review;
import org.springframework.data.jpa.repository.JpaRepository;
public interface ReviewRepository extends JpaRepository<Review, Long> { boolean existsByOrderId(Long orderId); }

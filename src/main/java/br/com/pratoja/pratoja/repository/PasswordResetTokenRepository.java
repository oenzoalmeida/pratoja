package br.com.pratoja.pratoja.repository;
import br.com.pratoja.pratoja.domain.PasswordResetToken;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetToken, Long> { Optional<PasswordResetToken> findByTokenHash(String tokenHash); void deleteByUser_Id(Long userId); }

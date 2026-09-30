package br.com.pratoja.pratoja.mail;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface MailOutboxRepository extends JpaRepository<MailOutbox, Long> {

    /** Linhas vencidas para o scheduler: nunca enviadas ou aguardando retry. */
    @Query("select m.id from MailOutbox m where m.status in ('PENDING', 'FAILED') and m.nextAttemptAt <= :now order by m.createdAt asc, m.id asc limit :max")
    List<Long> findDueIds(@Param("now") LocalDateTime now, @Param("max") int max);
}

package store.artistictech.consister.repository;

import store.artistictech.consister.entity.TransactionLog;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface TransactionLogRepository extends JpaRepository<TransactionLog, Long> {
    List<TransactionLog> findByAccountId(Long accountId);
}

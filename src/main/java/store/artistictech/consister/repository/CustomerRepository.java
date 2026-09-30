package store.artistictech.consister.repository;

import store.artistictech.consister.entity.Customer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import jakarta.persistence.LockModeType;
import java.util.Optional;

public interface CustomerRepository extends JpaRepository<Customer, Long> {
    Optional<Customer> findByEmail(String email);

    // Pessimistic Read Lock - multiple transactions can read but not modify
    @Lock(LockModeType.PESSIMISTIC_READ)
    @Query("SELECT c FROM Customer c WHERE c.id = :id")
    Optional<Customer> findByIdWithReadLock(Long id);

    // Pessimistic Write Lock - exclusive lock, prevents other transactions from reading or writing
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT c FROM Customer c WHERE c.id = :id")
    Optional<Customer> findByIdWithWriteLock(Long id);
}

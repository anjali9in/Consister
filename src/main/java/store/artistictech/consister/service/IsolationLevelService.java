package store.artistictech.consister.service;

import store.artistictech.consister.entity.Account;
import store.artistictech.consister.repository.AccountRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;

/**
 * Isolation Level Service - Demonstrates different transaction isolation levels
 * 
 * Topics covered:
 * 1. READ_UNCOMMITTED - Dirty reads possible
 * 2. READ_COMMITTED - Prevents dirty reads
 * 3. REPEATABLE_READ - Prevents dirty reads and non-repeatable reads
 * 4. SERIALIZABLE - Prevents all anomalies
 * 5. Performance vs Safety trade-offs
 * 
 * Isolation Levels Explained:
 * 
 * READ_UNCOMMITTED (Level 0):
 * - Lowest isolation level
 * - Allows dirty reads (reading uncommitted changes from other transactions)
 * - Performance: Best
 * - Safety: Worst
 * - Use case: Rarely used, only for non-critical reads
 * 
 * READ_COMMITTED (Level 1):
 * - Default in most databases
 * - Prevents dirty reads
 * - Allows non-repeatable reads (data changes between reads in same transaction)
 * - Performance: Good
 * - Safety: Good
 * - Use case: Most common choice for balanced performance/safety
 * 
 * REPEATABLE_READ (Level 2):
 * - Prevents dirty reads and non-repeatable reads
 * - Allows phantom reads (new rows appear when querying again)
 * - Performance: Moderate
 * - Safety: Better
 * - Use case: When data consistency within transaction is critical
 * 
 * SERIALIZABLE (Level 3):
 * - Highest isolation level
 * - Prevents all anomalies (dirty, non-repeatable, phantom reads)
 * - Performance: Worst (most locks)
 * - Safety: Best
 * - Use case: Critical operations where consistency is paramount
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class IsolationLevelService {

    private final AccountRepository accountRepository;

    /**
     * Scenario 1: READ_UNCOMMITTED isolation
     * - Can read uncommitted data from concurrent transactions
     * - RISK: Dirty reads possible
     */
    @Transactional(isolation = Isolation.READ_UNCOMMITTED)
    public BigDecimal getBalanceReadUncommitted(Long accountId) {
        log.info("Getting balance with READ_UNCOMMITTED (may see dirty reads)");
        
        Account account = accountRepository.findById(accountId)
                .orElseThrow(() -> new IllegalArgumentException("Account not found"));
        
        // If another transaction is updating this account but hasn't committed yet,
        // we might see the intermediate value
        return account.getBalance();
    }

    /**
     * Scenario 2: READ_COMMITTED isolation (default)
     * - Only reads committed data
     * - ISSUE: Non-repeatable reads possible
     */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void demonstrateNonRepeatableRead(Long accountId) {
        log.info("Demonstrating non-repeatable read scenario");
        
        // Read 1
        Account account1 = accountRepository.findById(accountId)
                .orElseThrow(() -> new IllegalArgumentException("Account not found"));
        BigDecimal balance1 = account1.getBalance();
        
        log.info("First read - Balance: {}", balance1);
        
        // Between read 1 and read 2, another transaction might commit a change
        // This simulates a delay
        try {
            Thread.sleep(100);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        // Read 2 - might return different value if concurrent transaction committed
        Account account2 = accountRepository.findById(accountId)
                .orElseThrow(() -> new IllegalArgumentException("Account not found"));
        BigDecimal balance2 = account2.getBalance();
        
        log.info("Second read - Balance: {}", balance2);
        
        if (!balance1.equals(balance2)) {
            log.warn("Non-repeatable read detected! Balance changed between reads");
        }
    }

    /**
     * Scenario 3: REPEATABLE_READ isolation
     * - Guarantees that if you read a row, you'll read the same value again
     * - ISSUE: Phantom reads possible (new rows might appear)
     */
    @Transactional(isolation = Isolation.REPEATABLE_READ)
    public void demonstratePhantomReadPrevention(Long accountId) {
        log.info("Demonstrating phantom read prevention");
        
        // Read same row multiple times
        Account account1 = accountRepository.findById(accountId)
                .orElseThrow(() -> new IllegalArgumentException("Account not found"));
        BigDecimal balance1 = account1.getBalance();
        
        try {
            Thread.sleep(100);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        // This read will return the same value (non-repeatable read is prevented)
        Account account2 = accountRepository.findById(accountId)
                .orElseThrow(() -> new IllegalArgumentException("Account not found"));
        BigDecimal balance2 = account2.getBalance();
        
        log.info("Repeatable read: {} == {}", balance1, balance2);
        assert balance1.equals(balance2) : "Balance should not change in REPEATABLE_READ";
    }

    /**
     * Scenario 4: SERIALIZABLE isolation
     * - Highest isolation level
     * - Transactions execute as if they were sequential
     * - Prevents all anomalies but slowest
     */
    @Transactional(isolation = Isolation.SERIALIZABLE)
    public void demonstrateSerializableIsolation(Long accountId) {
        log.info("Operating with SERIALIZABLE isolation (most restrictive)");
        
        Account account = accountRepository.findById(accountId)
                .orElseThrow(() -> new IllegalArgumentException("Account not found"));
        
        // This entire transaction operates with exclusive locks
        // Other transactions must wait for this one to complete
        
        account.setBalance(account.getBalance().add(new BigDecimal("100")));
        accountRepository.save(account);
        
        log.info("Transaction completed with full serialization");
    }

    /**
     * Scenario 5: Isolation level impact on concurrent operations
     * - Demonstrates the difference in behavior
     */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public BigDecimal getBalanceReadCommitted(Long accountId) {
        log.info("Getting balance with READ_COMMITTED");
        
        Account account = accountRepository.findById(accountId)
                .orElseThrow(() -> new IllegalArgumentException("Account not found"));
        
        return account.getBalance();
    }

    /**
     * Scenario 6: Multiple reads with REPEATABLE_READ
     * - Shows consistency of reads within same transaction
     */
    @Transactional(isolation = Isolation.REPEATABLE_READ)
    public void multipleReadsRepeatableRead(Long accountId) {
        log.info("Multiple reads with REPEATABLE_READ isolation");
        
        BigDecimal balance1 = getAccountBalance(accountId);
        BigDecimal balance2 = getAccountBalance(accountId);
        BigDecimal balance3 = getAccountBalance(accountId);
        
        log.info("All three reads return same value: {} = {} = {}", balance1, balance2, balance3);
        assert balance1.equals(balance2) && balance2.equals(balance3);
    }

    /**
     * Scenario 7: Update with SERIALIZABLE
     * - Shows exclusive locking behavior
     */
    @Transactional(isolation = Isolation.SERIALIZABLE)
    public void updateWithSerializableIsolation(Long accountId, BigDecimal amount) {
        log.info("Updating account with SERIALIZABLE isolation");
        
        Account account = accountRepository.findById(accountId)
                .orElseThrow(() -> new IllegalArgumentException("Account not found"));
        
        // This entire sequence is atomic and no other transaction can see partial updates
        BigDecimal newBalance = account.getBalance().add(amount);
        account.setBalance(newBalance);
        accountRepository.save(account);
        
        log.info("Account updated atomically");
    }

    /**
     * Scenario 8: Comparison of isolation levels
     * - Safe way to read without affecting concurrency
     */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public String analyzeBalance(Long accountId, BigDecimal threshold) {
        log.info("Analyzing account balance");
        
        Account account = accountRepository.findById(accountId)
                .orElseThrow(() -> new IllegalArgumentException("Account not found"));
        
        if (account.getBalance().compareTo(threshold) > 0) {
            return "OVER_THRESHOLD";
        } else if (account.getBalance().compareTo(threshold) < 0) {
            return "UNDER_THRESHOLD";
        } else {
            return "AT_THRESHOLD";
        }
    }

    /**
     * Helper: Get account balance
     */
    private BigDecimal getAccountBalance(Long accountId) {
        return accountRepository.findById(accountId)
                .map(Account::getBalance)
                .orElseThrow(() -> new IllegalArgumentException("Account not found"));
    }

    /**
     * Scenario 9: Demonstrating isolation level trade-offs
     * - READ_COMMITTED: Faster but might see inconsistent data
     * - SERIALIZABLE: Slower but guaranteed consistency
     */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void fastReadWithPotentialInconsistency(Long accountId) {
        log.info("Fast read with READ_COMMITTED (may see inconsistent state)");
        accountRepository.findById(accountId)
                .orElseThrow(() -> new IllegalArgumentException("Account not found"));
    }

    /**
     * Scenario 10: Safe concurrent operations
     * - Uses SERIALIZABLE only when needed
     */
    @Transactional(isolation = Isolation.SERIALIZABLE)
    public void safeTransferWithMaxIsolation(Long fromId, Long toId, BigDecimal amount) {
        log.info("Safe transfer with SERIALIZABLE isolation");
        
        Account from = accountRepository.findById(fromId)
                .orElseThrow(() -> new IllegalArgumentException("From account not found"));
        Account to = accountRepository.findById(toId)
                .orElseThrow(() -> new IllegalArgumentException("To account not found"));
        
        if (from.getBalance().compareTo(amount) < 0) {
            throw new IllegalArgumentException("Insufficient funds");
        }
        
        from.setBalance(from.getBalance().subtract(amount));
        to.setBalance(to.getBalance().add(amount));
        
        accountRepository.save(from);
        accountRepository.save(to);
        
        log.info("Transfer completed safely");
    }
}

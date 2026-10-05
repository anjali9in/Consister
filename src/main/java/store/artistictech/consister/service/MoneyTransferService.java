package store.artistictech.consister.service;

import store.artistictech.consister.entity.Account;
import store.artistictech.consister.entity.TransactionLog;
import store.artistictech.consister.exception.AccountStatusException;
import store.artistictech.consister.exception.InsufficientFundsException;
import store.artistictech.consister.repository.AccountRepository;
import store.artistictech.consister.repository.TransactionLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Money Transfer Service - Demonstrates real-world transaction scenarios
 * 
 * Topics covered:
 * 1. Rollback on business logic exceptions
 * 2. Isolation levels and concurrent access
 * 3. Atomicity: all-or-nothing principle in money transfer
 * 4. Compensation transactions
 * 5. Pessimistic locking for critical sections
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MoneyTransferService {

    private final AccountRepository accountRepository;
    private final TransactionLogRepository transactionLogRepository;

    /**
     * Scenario 1: Basic money transfer with rollback on insufficient funds
     * - Demonstrates transactional integrity
     * - Both accounts must be updated or none
     * - Logging happens in separate transaction (no rollback affect)
     */
    @Transactional
    public void transferMoney(Long fromAccountId, Long toAccountId, BigDecimal amount) {
        log.info("Starting money transfer: {} from account {} to {}", amount, fromAccountId, toAccountId);

        // Validate amount
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Transfer amount must be positive");
        }

        // Fetch source account
        Account fromAccount = accountRepository.findById(fromAccountId)
                .orElseThrow(() -> new IllegalArgumentException("Source account not found"));

        // Fetch destination account
        Account toAccount = accountRepository.findById(toAccountId)
                .orElseThrow(() -> new IllegalArgumentException("Destination account not found"));

        // Validate accounts are active
        if (fromAccount.getStatus() != Account.AccountStatus.ACTIVE) {
            throw new AccountStatusException("Source account is not active");
        }
        if (toAccount.getStatus() != Account.AccountStatus.ACTIVE) {
            throw new AccountStatusException("Destination account is not active");
        }

        // Check sufficient funds
        if (fromAccount.getBalance().compareTo(amount) < 0) {
            throw new InsufficientFundsException(
                    String.format("Insufficient funds. Available: %s, Required: %s", 
                            fromAccount.getBalance(), amount)
            );
        }

        // Debit from source
        fromAccount.setBalance(fromAccount.getBalance().subtract(amount));
        fromAccount.setLastTransactionAt(LocalDateTime.now());
        accountRepository.save(fromAccount);
        log.info("Debited {} from account {}", amount, fromAccountId);

        // Credit to destination
        toAccount.setBalance(toAccount.getBalance().add(amount));
        toAccount.setLastTransactionAt(LocalDateTime.now());
        accountRepository.save(toAccount);
        log.info("Credited {} to account {}", amount, toAccountId);

        // Log successful transaction
        logTransaction(fromAccountId, "TRANSFER_OUT", "Transferred to account " + toAccountId, TransactionLog.TransactionStatus.SUCCESS);
    }

    /**
     * Scenario 2: Transfer with compensation (rollback simulation)
     * - Demonstrates handling of partial failures
     * - Compensation logic to undo partial transactions
     */
    @Transactional
    public void transferMoneyWithCompensation(Long fromAccountId, Long toAccountId, BigDecimal amount) {
        log.info("Starting transfer with compensation: {} from {} to {}", amount, fromAccountId, toAccountId);

        try {
            // Step 1: Debit from source
            Account fromAccount = accountRepository.findByIdWithWriteLock(fromAccountId)
                    .orElseThrow(() -> new IllegalArgumentException("Source account not found"));

            if (fromAccount.getBalance().compareTo(amount) < 0) {
                throw new InsufficientFundsException("Insufficient funds for transfer");
            }

            fromAccount.setBalance(fromAccount.getBalance().subtract(amount));
            accountRepository.save(fromAccount);

            // Step 2: Credit to destination
            Account toAccount = accountRepository.findByIdWithWriteLock(toAccountId)
                    .orElseThrow(() -> new IllegalArgumentException("Destination account not found"));

            toAccount.setBalance(toAccount.getBalance().add(amount));
            accountRepository.save(toAccount);

            log.info("Transfer completed successfully");
        } catch (InsufficientFundsException e) {
            log.error("Transfer failed: {}", e.getMessage());
            logTransaction(fromAccountId, "TRANSFER_FAILED", e.getMessage(), TransactionLog.TransactionStatus.FAILED);
            throw e; // Trigger rollback
        }
    }

    /**
     * Scenario 3: Transfer with isolation level control
     * - SERIALIZABLE: Prevents dirty reads, non-repeatable reads, phantom reads
     * - Most restrictive but safest for critical operations
     */
    @Transactional(isolation = Isolation.SERIALIZABLE)
    public void transferMoneySerializable(Long fromAccountId, Long toAccountId, BigDecimal amount) {
        log.info("Transfer with SERIALIZABLE isolation level");
        transferMoney(fromAccountId, toAccountId, amount);
    }

    /**
     * Scenario 4: Transfer with READ_COMMITTED isolation
     * - Prevents dirty reads but allows non-repeatable reads and phantom reads
     * - Good balance between safety and performance
     */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void transferMoneyReadCommitted(Long fromAccountId, Long toAccountId, BigDecimal amount) {
        log.info("Transfer with READ_COMMITTED isolation level");
        transferMoney(fromAccountId, toAccountId, amount);
    }

    /**
     * Scenario 5: Transfer with pessimistic locking
     * - Prevents concurrent modifications
     * - Uses database-level locks
     */
    @Transactional
    public void transferMoneyWithPessimisticLock(Long fromAccountId, Long toAccountId, BigDecimal amount) {
        log.info("Transfer with pessimistic locking");

        // Lock both accounts to prevent concurrent modifications
        Account fromAccount = accountRepository.findByIdWithWriteLock(fromAccountId)
                .orElseThrow(() -> new IllegalArgumentException("Source account not found"));

        Account toAccount = accountRepository.findByIdWithWriteLock(toAccountId)
                .orElseThrow(() -> new IllegalArgumentException("Destination account not found"));

        if (fromAccount.getBalance().compareTo(amount) < 0) {
            throw new InsufficientFundsException("Insufficient funds");
        }

        fromAccount.setBalance(fromAccount.getBalance().subtract(amount));
        toAccount.setBalance(toAccount.getBalance().add(amount));

        accountRepository.save(fromAccount);
        accountRepository.save(toAccount);

        log.info("Pessimistic transfer completed");
    }

    /**
     * Scenario 6: Nested transaction for logging
     * - Logging should not affect main transaction
     * - Uses REQUIRES_NEW to ensure logging commits independently
     */
    @Transactional
    public void transferWithIndependentLogging(Long fromAccountId, Long toAccountId, BigDecimal amount) {
        log.info("Transfer with independent logging");
        transferMoney(fromAccountId, toAccountId, amount);
        logTransactionIndependently(fromAccountId, "TRANSFER_OUT", "Successful transfer");
    }

    /**
     * Helper method: Log transaction in separate transaction
     * If logging fails, main transaction still commits
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void logTransaction(Long accountId, String type, String description, TransactionLog.TransactionStatus status) {
        try {
            TransactionLog log = TransactionLog.builder()
                    .accountId(accountId)
                    .transactionType(type)
                    .description(description)
                    .status(status)
                    .build();
            transactionLogRepository.save(log);
            log.info("Transaction logged successfully");
        } catch (Exception e) {
            log.error("Failed to log transaction", e);
            // Don't throw - logging failure shouldn't affect business logic
        }
    }

    /**
     * Helper method: Log transaction independently
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    private void logTransactionIndependently(Long accountId, String type, String description) {
        TransactionLog transLog = TransactionLog.builder()
                .accountId(accountId)
                .transactionType(type)
                .description(description)
                .status(TransactionLog.TransactionStatus.SUCCESS)
                .build();
        transactionLogRepository.save(transLog);
    }

    /**
     * Scenario 7: Account deposit
     */
    @Transactional
    public void depositMoney(Long accountId, BigDecimal amount) {
        log.info("Depositing {} to account {}", amount, accountId);

        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Deposit amount must be positive");
        }

        Account account = accountRepository.findById(accountId)
                .orElseThrow(() -> new IllegalArgumentException("Account not found"));

        if (account.getStatus() != Account.AccountStatus.ACTIVE) {
            throw new AccountStatusException("Account is not active");
        }

        account.setBalance(account.getBalance().add(amount));
        account.setLastTransactionAt(LocalDateTime.now());
        accountRepository.save(account);

        logTransaction(accountId, "DEPOSIT", "Deposited " + amount, TransactionLog.TransactionStatus.SUCCESS);
    }

    /**
     * Scenario 8: Account withdrawal
     */
    @Transactional
    public void withdrawMoney(Long accountId, BigDecimal amount) {
        log.info("Withdrawing {} from account {}", amount, accountId);

        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Withdrawal amount must be positive");
        }

        Account account = accountRepository.findById(accountId)
                .orElseThrow(() -> new IllegalArgumentException("Account not found"));

        if (account.getBalance().compareTo(amount) < 0) {
            throw new InsufficientFundsException("Insufficient funds for withdrawal");
        }

        account.setBalance(account.getBalance().subtract(amount));
        account.setLastTransactionAt(LocalDateTime.now());
        accountRepository.save(account);

        logTransaction(accountId, "WITHDRAWAL", "Withdrawn " + amount, TransactionLog.TransactionStatus.SUCCESS);
    }

    /**
     * Scenario 9: Get account balance (read-only)
     */
    @Transactional(readOnly = true)
    public BigDecimal getAccountBalance(Long accountId) {
        return accountRepository.findById(accountId)
                .map(Account::getBalance)
                .orElseThrow(() -> new IllegalArgumentException("Account not found"));
    }

    /**
     * Scenario 10: Create account
     */
    @Transactional
    public Account createAccount(String accountNumber, String holderName, BigDecimal initialBalance) {
        log.info("Creating account: {}", accountNumber);

        if (initialBalance.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Initial balance cannot be negative");
        }

        Account account = Account.builder()
                .accountNumber(accountNumber)
                .holderName(holderName)
                .balance(initialBalance)
                .status(Account.AccountStatus.ACTIVE)
                .build();

        return accountRepository.save(account);
    }
}

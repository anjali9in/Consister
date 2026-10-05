package store.artistictech.consister.service;

import store.artistictech.consister.entity.Account;
import store.artistictech.consister.repository.AccountRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;

/**
 * Transaction Context Service - Demonstrates transaction propagation and context
 * 
 * Topics covered:
 * 1. Transaction propagation strategies
 * 2. When to use REQUIRES_NEW, NESTED, etc.
 * 3. Transaction context flow
 * 4. Resource sharing between transactions
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TransactionContextService {

    private final AccountRepository accountRepository;

    /**
     * Scenario 1: REQUIRED propagation (default)
     * - If transaction exists, join it
     * - If not, create new one
     */
    @Transactional(propagation = Propagation.REQUIRED)
    public void methodWithRequired() {
        log.info("Method with PROPAGATION_REQUIRED");
        Account account = new Account();
        account.setAccountNumber("REQ001");
        account.setHolderName("Required Test");
        account.setBalance(BigDecimal.ZERO);
        accountRepository.save(account);
    }

    /**
     * Scenario 2: REQUIRES_NEW propagation
     * - Always creates new transaction
     * - Suspends parent if exists
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void methodWithRequiresNew() {
        log.info("Method with PROPAGATION_REQUIRES_NEW");
        Account account = new Account();
        account.setAccountNumber("RN001");
        account.setHolderName("Requires New Test");
        account.setBalance(new BigDecimal("100"));
        accountRepository.save(account);
    }

    /**
     * Scenario 3: NESTED propagation
     * - Creates savepoint if database supports it
     * - Nested transaction can rollback independently
     */
    @Transactional(propagation = Propagation.NESTED)
    public void methodWithNested() {
        log.info("Method with PROPAGATION_NESTED (savepoint)");
        Account account = new Account();
        account.setAccountNumber("NEST001");
        account.setHolderName("Nested Test");
        account.setBalance(new BigDecimal("200"));
        accountRepository.save(account);
    }

    /**
     * Scenario 4: SUPPORTS propagation
     * - If transaction exists, participate
     * - If not, execute non-transactionally
     */
    @Transactional(propagation = Propagation.SUPPORTS)
    public Account getAccountSupports(Long id) {
        log.info("Getting account with PROPAGATION_SUPPORTS");
        return accountRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Account not found"));
    }

    /**
     * Scenario 5: NOT_SUPPORTED propagation
     * - Suspends current transaction
     * - Executes non-transactionally
     */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public Account getAccountNotSupported(Long id) {
        log.info("Getting account with PROPAGATION_NOT_SUPPORTED (no transaction)");
        return accountRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Account not found"));
    }

    /**
     * Scenario 6: MANDATORY propagation
     * - Requires existing transaction
     * - Throws exception if none exists
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void methodMandatory(Long id, BigDecimal amount) {
        log.info("Method with PROPAGATION_MANDATORY - REQUIRES existing transaction!");
        
        Account account = accountRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Account not found"));
        
        account.setBalance(account.getBalance().add(amount));
        accountRepository.save(account);
    }

    /**
     * Scenario 7: NEVER propagation
     * - Must NOT be in a transaction
     * - Throws exception if transaction exists
     */
    @Transactional(propagation = Propagation.NEVER)
    public void methodNever(Long id) {
        log.info("Method with PROPAGATION_NEVER - Must be called outside transaction!");
        accountRepository.findById(id).orElseThrow(() -> new IllegalArgumentException("Not found"));
    }

    /**
     * Scenario 8: Caller method coordinating multiple transactions
     * - Shows how to manage transaction propagation from caller perspective
     */
    @Transactional
    public void coordinatingMultipleTransactions() {
        log.info("Coordinating multiple transactions");
        
        // This executes in parent transaction
        methodWithRequired();
        
        // This creates independent transaction
        methodWithRequiresNew();
        
        // Back to parent transaction
        methodWithRequired();
    }

    /**
     * Scenario 9: Complex transaction propagation chain
     * - Multiple levels of method calls with different propagation
     */
    @Transactional(propagation = Propagation.REQUIRED)
    public void complexPropagationChain() {
        log.info("Starting complex propagation chain");
        
        // Level 1: REQUIRED
        Account acc1 = createAccountRequired("ACC1");
        
        // Level 2: Create REQUIRES_NEW
        updateAccountRequiresNew(acc1.getId());
        
        // Level 2: Create NESTED
        updateAccountNested(acc1.getId());
        
        log.info("Complex chain completed");
    }

    /**
     * Helper: Create account in current transaction
     */
    @Transactional(propagation = Propagation.REQUIRED)
    private Account createAccountRequired(String accountNumber) {
        log.info("Creating account in REQUIRED transaction");
        Account account = new Account();
        account.setAccountNumber(accountNumber);
        account.setHolderName("Test");
        account.setBalance(BigDecimal.ZERO);
        return accountRepository.save(account);
    }

    /**
     * Helper: Update in REQUIRES_NEW transaction
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    private void updateAccountRequiresNew(Long id) {
        log.info("Updating account in REQUIRES_NEW transaction");
        Account account = accountRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Not found"));
        account.setBalance(account.getBalance().add(BigDecimal.TEN));
        accountRepository.save(account);
    }

    /**
     * Helper: Update in NESTED transaction
     */
    @Transactional(propagation = Propagation.NESTED)
    private void updateAccountNested(Long id) {
        log.info("Updating account in NESTED transaction");
        Account account = accountRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Not found"));
        account.setBalance(account.getBalance().add(BigDecimal.TEN));
        accountRepository.save(account);
    }

    /**
     * Scenario 10: Transaction context demonstration
     * - Shows how transaction context flows through method calls
     */
    @Transactional
    public void demonstrateTransactionContext() {
        log.info("Transaction context flow demonstration");
        
        // This is in transaction context
        Account account1 = accountRepository.save(Account.builder()
                .accountNumber("CTX1")
                .holderName("Context Test 1")
                .balance(BigDecimal.ZERO)
                .build());
        
        // Calling non-transactional method - still uses parent transaction
        demonstrateContextInNestedCall(account1.getId());
        
        log.info("Transaction context demonstration completed");
    }

    /**
     * Non-transactional method - inherits parent transaction
     */
    private void demonstrateContextInNestedCall(Long id) {
        log.info("In nested non-transactional method, but in transaction context");
        
        Account account = accountRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Not found"));
        
        account.setBalance(new BigDecimal("100"));
        // This save uses parent transaction context
        accountRepository.save(account);
    }
}

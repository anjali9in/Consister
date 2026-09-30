package store.artistictech.consister.service;

import store.artistictech.consister.entity.Customer;
import store.artistictech.consister.repository.CustomerRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Basic Transaction Service - Demonstrates fundamental transaction concepts
 * 
 * Topics covered:
 * 1. @Transactional annotation basics
 * 2. Transaction propagation (REQUIRED, REQUIRES_NEW, NESTED, etc.)
 * 3. Read-only transactions
 * 4. Rollback and commit behavior
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BasicTransactionService {

    private final CustomerRepository customerRepository;

    /**
     * Scenario 1: Basic transaction with default behavior (PROPAGATION_REQUIRED)
     * - If no transaction exists, creates a new one
     * - If transaction exists, joins it
     * - All-or-nothing principle: either all changes commit or all rollback
     */
    @Transactional
    public Customer createCustomer(String name, String email, String phone) {
        log.info("Creating customer: {} with email: {}", name, email);
        
        Customer customer = Customer.builder()
                .name(name)
                .email(email)
                .phone(phone)
                .build();
        
        Customer saved = customerRepository.save(customer);
        log.info("Customer created with ID: {}", saved.getId());
        return saved;
    }

    /**
     * Scenario 2: Read-only transaction
     * - Optimizes performance as database knows no writes will occur
     * - Prevents accidental modifications
     */
    @Transactional(readOnly = true)
    public Customer getCustomerById(Long id) {
        log.info("Fetching customer with ID: {}", id);
        return customerRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Customer not found with ID: " + id));
    }

    /**
     * Scenario 3: Transaction with rollback
     * - Demonstrates what happens when an exception occurs
     * - By default, RuntimeExceptions trigger rollback
     */
    @Transactional
    public Customer createAndValidateCustomer(String name, String email, String phone) {
        log.info("Creating customer with validation");
        
        if (email == null || email.trim().isEmpty()) {
            throw new IllegalArgumentException("Email cannot be empty");
        }
        
        Customer customer = Customer.builder()
                .name(name)
                .email(email)
                .phone(phone)
                .build();
        
        return customerRepository.save(customer);
    }

    /**
     * Scenario 4: REQUIRES_NEW propagation
     * - Always creates a new transaction
     * - Parent transaction is suspended
     * - Independent commit/rollback
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Customer createCustomerInNewTransaction(String name, String email, String phone) {
        log.info("Creating customer in NEW transaction (parent suspended)");
        
        Customer customer = Customer.builder()
                .name(name)
                .email(email)
                .phone(phone)
                .build();
        
        return customerRepository.save(customer);
    }

    /**
     * Scenario 5: NESTED propagation (if database supports savepoints)
     * - Creates a savepoint within current transaction
     * - Can rollback to savepoint without affecting parent
     */
    @Transactional(propagation = Propagation.NESTED)
    public Customer createCustomerWithNestedTransaction(String name, String email, String phone) {
        log.info("Creating customer with NESTED transaction (savepoint)");
        
        Customer customer = Customer.builder()
                .name(name)
                .email(email)
                .phone(phone)
                .build();
        
        return customerRepository.save(customer);
    }

    /**
     * Scenario 6: NOT_SUPPORTED propagation
     * - Executes outside any transaction
     * - Existing transaction is suspended
     */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public Customer getCustomerNoTransaction(Long id) {
        log.info("Getting customer WITHOUT transaction");
        return customerRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Customer not found"));
    }

    /**
     * Scenario 7: Checked exceptions - don't cause rollback by default
     * Configure with rollbackFor to rollback on specific checked exceptions
     */
    @Transactional(rollbackFor = Exception.class)
    public Customer createCustomerWithCheckedExceptionHandling(String name, String email, String phone) throws Exception {
        log.info("Creating customer with checked exception handling");
        
        Customer customer = Customer.builder()
                .name(name)
                .email(email)
                .phone(phone)
                .build();
        
        return customerRepository.save(customer);
    }

    /**
     * Scenario 8: Update with transaction
     * - Demonstrates optimistic locking with @Version
     * - Concurrent updates are handled safely
     */
    @Transactional
    public Customer updateCustomer(Long id, String name, String email, String phone) {
        log.info("Updating customer with ID: {}", id);
        
        Customer customer = customerRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Customer not found"));
        
        customer.setName(name);
        customer.setEmail(email);
        customer.setPhone(phone);
        
        return customerRepository.save(customer);
    }

    /**
     * Scenario 9: Delete with transaction
     */
    @Transactional
    public void deleteCustomer(Long id) {
        log.info("Deleting customer with ID: {}", id);
        
        Customer customer = customerRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Customer not found"));
        
        customerRepository.delete(customer);
    }

    /**
     * Scenario 10: MANDATORY propagation
     * - Requires existing transaction
     * - Throws exception if no transaction exists
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void validateCustomerEmail(Long id, String newEmail) {
        log.info("Validating customer email - REQUIRES existing transaction");
        
        Customer customer = customerRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Customer not found"));
        
        if (newEmail != null && !newEmail.contains("@")) {
            throw new IllegalArgumentException("Invalid email format");
        }
    }
}

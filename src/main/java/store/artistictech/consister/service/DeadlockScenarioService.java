package store.artistictech.consister.service;

import store.artistictech.consister.entity.Inventory;
import store.artistictech.consister.repository.InventoryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Deadlock Scenario Service - Demonstrates and resolves deadlock situations
 * 
 * Topics covered:
 * 1. What causes deadlocks
 * 2. How to detect deadlocks
 * 3. Prevention strategies
 * 4. Recovery from deadlocks
 * 
 * Deadlock Definition:
 * A deadlock occurs when two or more transactions hold locks on different resources
 * and each transaction needs to acquire the lock held by the other transaction.
 * 
 * Classic Deadlock Example:
 * Thread 1: Lock(A), then try Lock(B) - but blocked by Thread 2 holding B
 * Thread 2: Lock(B), then try Lock(A) - but blocked by Thread 1 holding A
 * Result: Neither thread can proceed = DEADLOCK
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DeadlockScenarioService {

    private final InventoryRepository inventoryRepository;

    /**
     * Scenario 1: DEADLOCK - Inconsistent lock ordering
     * - Two threads lock resources in different order
     * - PROBLEM: Can cause deadlock
     */
    @Transactional
    public void deadlockScenarioInconsistentOrder(Long inventoryId1, Long inventoryId2, Integer qty1, Integer qty2) {
        log.info("DEADLOCK SCENARIO: Locking in potentially inconsistent order");
        
        // This method locks inventoryId1 first, then inventoryId2
        // If another thread locks them in reverse order, DEADLOCK possible
        
        Inventory inv1 = inventoryRepository.findByIdWithWriteLock(inventoryId1)
                .orElseThrow(() -> new IllegalArgumentException("Inventory 1 not found"));
        
        // Simulate some processing time (increases deadlock likelihood)
        try {
            Thread.sleep(50);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        Inventory inv2 = inventoryRepository.findByIdWithWriteLock(inventoryId2)
                .orElseThrow(() -> new IllegalArgumentException("Inventory 2 not found"));
        
        updateInventory(inv1, qty1);
        updateInventory(inv2, qty2);
        
        inventoryRepository.save(inv1);
        inventoryRepository.save(inv2);
        
        log.info("Operation completed (or deadlock timeout occurred)");
    }

    /**
     * Scenario 2: DEADLOCK PREVENTION - Consistent lock ordering
     * - Always acquire locks in same order (e.g., by ID)
     * - SOLUTION: Prevents circular wait condition
     */
    @Transactional
    public void preventDeadlockWithOrdering(Long inventoryId1, Long inventoryId2, Integer qty1, Integer qty2) {
        log.info("DEADLOCK PREVENTION: Consistent lock ordering");
        
        // Always acquire locks in ascending order
        Long firstId = Math.min(inventoryId1, inventoryId2);
        Long secondId = Math.max(inventoryId1, inventoryId2);
        
        Inventory inv1 = inventoryRepository.findByIdWithWriteLock(firstId)
                .orElseThrow(() -> new IllegalArgumentException("Inventory 1 not found"));
        
        Inventory inv2 = inventoryRepository.findByIdWithWriteLock(secondId)
                .orElseThrow(() -> new IllegalArgumentException("Inventory 2 not found"));
        
        Integer qty1ToUse = firstId.equals(inventoryId1) ? qty1 : qty2;
        Integer qty2ToUse = secondId.equals(inventoryId2) ? qty2 : qty1;
        
        updateInventory(inv1, qty1ToUse);
        updateInventory(inv2, qty2ToUse);
        
        inventoryRepository.save(inv1);
        inventoryRepository.save(inv2);
        
        log.info("Operation completed without deadlock risk");
    }

    /**
     * Scenario 3: DEADLOCK RECOVERY - Retry logic
     * - Some applications handle deadlock exceptions and retry
     * - STRATEGY: Exponential backoff to avoid repeated deadlock
     */
    @Transactional
    public void transferWithRetryLogic(Long fromId, Long toId, Integer qty) {
        log.info("Transfer with implicit retry capability");
        
        // In real application, wrap this in retry logic
        Inventory from = inventoryRepository.findByIdWithWriteLock(fromId)
                .orElseThrow(() -> new IllegalArgumentException("From not found"));
        Inventory to = inventoryRepository.findByIdWithWriteLock(toId)
                .orElseThrow(() -> new IllegalArgumentException("To not found"));
        
        if (from.getQuantity() < qty) {
            throw new IllegalArgumentException("Insufficient quantity");
        }
        
        from.setQuantity(from.getQuantity() - qty);
        to.setQuantity(to.getQuantity() + qty);
        
        inventoryRepository.save(from);
        inventoryRepository.save(to);
        
        log.info("Transfer completed");
    }

    /**
     * Scenario 4: DEADLOCK PREVENTION - Reduced lock scope
     * - Hold locks for minimum time necessary
     * - STRATEGY: Do not perform long-running operations inside transaction
     */
    @Transactional
    public void minimizeLockHoldingTime(Long inventoryId, Integer newQty) {
        log.info("Minimizing lock holding time");
        
        // Acquire lock only when actually needed
        Inventory inventory = inventoryRepository.findByIdWithWriteLock(inventoryId)
                .orElseThrow(() -> new IllegalArgumentException("Inventory not found"));
        
        // Simple update - minimal lock duration
        inventory.setQuantity(newQty);
        inventoryRepository.save(inventory);
        
        log.info("Lock released quickly");
    }

    /**
     * Scenario 5: DEADLOCK - Resource access in nested calls
     * - Nested method calls holding locks
     * - RISK: Complex dependency graphs can lead to deadlock
     */
    @Transactional
    public void riskyNestedLockingScenario(Long inventoryId1, Long inventoryId2) {
        log.info("Risky nested locking scenario");
        
        Inventory inv1 = inventoryRepository.findByIdWithWriteLock(inventoryId1)
                .orElseThrow(() -> new IllegalArgumentException("Not found"));
        
        // Calling method that locks different resource
        nestedMethodThatAcquiresLock(inventoryId2);
        
        inventoryRepository.save(inv1);
    }

    /**
     * Nested method that acquires lock (part of deadlock scenario)
     */
    private void nestedMethodThatAcquiresLock(Long inventoryId) {
        Inventory inv = inventoryRepository.findByIdWithWriteLock(inventoryId)
                .orElseThrow(() -> new IllegalArgumentException("Not found"));
        inv.setQuantity(inv.getQuantity() + 1);
        inventoryRepository.save(inv);
    }

    /**
     * Scenario 6: DEADLOCK PREVENTION - Using SERIALIZABLE wisely
     * - SERIALIZABLE prevents deadlock by design (sequential execution)
     * - TRADE-OFF: Lower throughput
     */
    @Transactional(isolation = Isolation.SERIALIZABLE)
    public void serializablePreventedDeadlock(Long inventoryId1, Long inventoryId2, Integer qty1, Integer qty2) {
        log.info("SERIALIZABLE isolation prevents deadlock");
        
        // With SERIALIZABLE, transactions are serialized
        // No possibility of deadlock, but slower
        
        Inventory inv1 = inventoryRepository.findById(inventoryId1)
                .orElseThrow(() -> new IllegalArgumentException("Not found"));
        Inventory inv2 = inventoryRepository.findById(inventoryId2)
                .orElseThrow(() -> new IllegalArgumentException("Not found"));
        
        updateInventory(inv1, qty1);
        updateInventory(inv2, qty2);
        
        inventoryRepository.save(inv1);
        inventoryRepository.save(inv2);
    }

    /**
     * Scenario 7: DEADLOCK - Multiple entity updates
     * - Updating different entities in different order
     * - RISK: Deadlock if threads reverse order
     */
    @Transactional
    public void multipleEntityUpdateRiskyOrder(Long id1, Long id2) {
        log.info("Multiple entity update in risky order");
        
        // Risk: If called from another thread with id1 and id2 reversed,
        // could cause deadlock
        Inventory inv1 = inventoryRepository.findByIdWithWriteLock(id1)
                .orElseThrow(() -> new IllegalArgumentException("Not found"));
        Inventory inv2 = inventoryRepository.findByIdWithWriteLock(id2)
                .orElseThrow(() -> new IllegalArgumentException("Not found"));
        
        inv1.setQuantity(inv1.getQuantity() + 1);
        inv2.setQuantity(inv2.getQuantity() + 1);
        
        inventoryRepository.save(inv1);
        inventoryRepository.save(inv2);
    }

    /**
     * Scenario 8: DEADLOCK PREVENTION - Timeout configuration
     * - Database-level timeout prevents infinite waiting
     * - STRATEGY: Rely on database to detect and kill deadlock
     */
    @Transactional
    public void operationWithTimeoutProtection(Long inventoryId) {
        log.info("Operation with timeout protection (database-level)");
        
        // Actual timeout is configured at database/driver level
        // Application should handle SQLGrammarException or lock timeout exception
        
        Inventory inventory = inventoryRepository.findByIdWithWriteLock(inventoryId)
                .orElseThrow(() -> new IllegalArgumentException("Not found"));
        
        inventory.setQuantity(inventory.getQuantity() + 1);
        inventoryRepository.save(inventory);
    }

    /**
     * Scenario 9: DEADLOCK PREVENTION - Acquire all locks upfront
     * - Request all needed locks before starting work
     * - STRATEGY: No need to acquire new locks while holding existing ones
     */
    @Transactional
    public void acquireAllLocksUpfront(Long... inventoryIds) {
        log.info("Acquiring all locks upfront");
        
        // Sort IDs to ensure consistent ordering
        java.util.Arrays.sort(inventoryIds);
        
        java.util.List<Inventory> inventories = new java.util.ArrayList<>();
        for (Long id : inventoryIds) {
            Inventory inv = inventoryRepository.findByIdWithWriteLock(id)
                    .orElseThrow(() -> new IllegalArgumentException("Not found"));
            inventories.add(inv);
        }
        
        // Now perform all updates
        for (Inventory inv : inventories) {
            inv.setQuantity(inv.getQuantity() + 1);
            inventoryRepository.save(inv);
        }
        
        log.info("All operations completed successfully");
    }

    /**
     * Scenario 10: DEADLOCK DETECTION & RESOLUTION
     * - Monitor lock wait times
     * - Detect potential deadlocks before they happen
     */
    @Transactional
    public void deadlockDetectionScenario(Long inventoryId) {
        log.info("Deadlock detection scenario");
        
        long startTime = System.currentTimeMillis();
        
        try {
            Inventory inventory = inventoryRepository.findByIdWithWriteLock(inventoryId)
                    .orElseThrow(() -> new IllegalArgumentException("Not found"));
            
            long lockAcquisitionTime = System.currentTimeMillis() - startTime;
            
            if (lockAcquisitionTime > 1000) {
                log.warn("Took longer than expected to acquire lock: {}ms", lockAcquisitionTime);
                // Could indicate potential deadlock or heavy contention
            }
            
            inventory.setQuantity(inventory.getQuantity() + 1);
            inventoryRepository.save(inventory);
        } catch (Exception e) {
            if (e.getMessage() != null && e.getMessage().contains("Deadlock")) {
                log.error("Deadlock detected! Consider retry logic");
                throw e;
            }
        }
    }

    /**
     * Helper method: Update inventory
     */
    private void updateInventory(Inventory inventory, Integer quantity) {
        int newQty = inventory.getQuantity() + quantity;
        if (newQty < 0) {
            throw new IllegalArgumentException("Cannot have negative quantity");
        }
        inventory.setQuantity(newQty);
    }
}

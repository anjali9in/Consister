package store.artistictech.consister.service;

import store.artistictech.consister.entity.Inventory;
import store.artistictech.consister.exception.OutOfStockException;
import store.artistictech.consister.repository.InventoryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;

/**
 * Inventory Service - Demonstrates concurrent modification scenarios
 * 
 * Topics covered:
 * 1. Pessimistic locking for inventory updates
 * 2. Race conditions and how to prevent them
 * 3. Deadlock scenarios
 * 4. Isolation levels for inventory checking
 * 5. Stock validation in transactions
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class InventoryService {

    private final InventoryRepository inventoryRepository;

    /**
     * Scenario 1: Basic stock reduction (vulnerable to race conditions)
     * - Without proper locking, can lead to overselling
     */
    @Transactional
    public void reduceStockBasic(Long inventoryId, Integer quantity) {
        log.info("Reducing stock for inventory {}: {}", inventoryId, quantity);

        if (quantity <= 0) {
            throw new IllegalArgumentException("Quantity must be positive");
        }

        Inventory inventory = inventoryRepository.findById(inventoryId)
                .orElseThrow(() -> new IllegalArgumentException("Inventory not found"));

        if (inventory.getQuantity() < quantity) {
            throw new OutOfStockException(
                    String.format("Insufficient stock. Available: %d, Required: %d", 
                            inventory.getQuantity(), quantity)
            );
        }

        inventory.setQuantity(inventory.getQuantity() - quantity);
        inventoryRepository.save(inventory);

        log.info("Stock reduced successfully. Remaining: {}", inventory.getQuantity());
    }

    /**
     * Scenario 2: Stock reduction with pessimistic locking
     * - Uses database lock to prevent concurrent modifications
     * - Guarantees data consistency
     */
    @Transactional
    public void reduceStockWithLock(Long inventoryId, Integer quantity) {
        log.info("Reducing stock WITH pessimistic lock: {}", inventoryId);

        Inventory inventory = inventoryRepository.findByIdWithWriteLock(inventoryId)
                .orElseThrow(() -> new IllegalArgumentException("Inventory not found"));

        if (inventory.getQuantity() < quantity) {
            throw new OutOfStockException("Insufficient stock");
        }

        inventory.setQuantity(inventory.getQuantity() - quantity);
        inventory.setLastRestockedAt(LocalDateTime.now());
        inventoryRepository.save(inventory);
    }

    /**
     * Scenario 3: Restock with isolation level control
     * - SERIALIZABLE ensures no phantom reads during stock level checks
     */
    @Transactional(isolation = Isolation.SERIALIZABLE)
    public void restockWithSerialization(Long inventoryId, Integer quantity) {
        log.info("Restocking with SERIALIZABLE isolation");

        if (quantity <= 0) {
            throw new IllegalArgumentException("Restock quantity must be positive");
        }

        Inventory inventory = inventoryRepository.findById(inventoryId)
                .orElseThrow(() -> new IllegalArgumentException("Inventory not found"));

        int newQuantity = inventory.getQuantity() + quantity;

        if (newQuantity > inventory.getMaximumStock()) {
            throw new IllegalArgumentException("Restock would exceed maximum stock level");
        }

        inventory.setQuantity(newQuantity);
        inventory.setLastRestockedAt(LocalDateTime.now());
        inventory.setStatus(newQuantity > inventory.getMinimumStock() 
                ? Inventory.InventoryStatus.IN_STOCK 
                : Inventory.InventoryStatus.LOW_STOCK);

        inventoryRepository.save(inventory);
    }

    /**
     * Scenario 4: Multiple product restock (deadlock scenario)
     * - If two threads lock products in different order, can cause deadlock
     * - Solution: Always lock in consistent order
     */
    @Transactional
    public void restockMultipleProductsOrderedLock(Long inventoryId1, Long inventoryId2, Integer quantity1, Integer quantity2) {
        log.info("Restocking multiple products with ordered locking");

        // Always lock in ascending order to prevent deadlock
        Long firstId = Math.min(inventoryId1, inventoryId2);
        Long secondId = Math.max(inventoryId1, inventoryId2);

        Inventory inv1 = inventoryRepository.findByIdWithWriteLock(firstId)
                .orElseThrow(() -> new IllegalArgumentException("First inventory not found"));

        Inventory inv2 = inventoryRepository.findByIdWithWriteLock(secondId)
                .orElseThrow(() -> new IllegalArgumentException("Second inventory not found"));

        updateInventoryQuantity(inv1, quantity1);
        updateInventoryQuantity(inv2, quantity2);

        inventoryRepository.save(inv1);
        inventoryRepository.save(inv2);

        log.info("Multiple product restock completed");
    }

    /**
     * Scenario 5: Check stock and apply discount if low
     * - Demonstrates read-only transaction with decision logic
     */
    @Transactional(readOnly = true)
    public boolean isStockLow(Long inventoryId) {
        log.info("Checking if stock is low for inventory: {}", inventoryId);

        return inventoryRepository.findById(inventoryId)
                .map(inv -> inv.getQuantity() <= inv.getMinimumStock())
                .orElse(false);
    }

    /**
     * Scenario 6: Update stock status based on quantity
     */
    @Transactional
    public void updateStockStatus(Long inventoryId) {
        log.info("Updating stock status for inventory: {}", inventoryId);

        Inventory inventory = inventoryRepository.findById(inventoryId)
                .orElseThrow(() -> new IllegalArgumentException("Inventory not found"));

        if (inventory.getQuantity() == 0) {
            inventory.setStatus(Inventory.InventoryStatus.OUT_OF_STOCK);
        } else if (inventory.getQuantity() <= inventory.getMinimumStock()) {
            inventory.setStatus(Inventory.InventoryStatus.LOW_STOCK);
        } else {
            inventory.setStatus(Inventory.InventoryStatus.IN_STOCK);
        }

        inventoryRepository.save(inventory);
    }

    /**
     * Scenario 7: Create inventory
     */
    @Transactional
    public Inventory createInventory(String productCode, String productName, Integer initialQuantity, 
                                      Integer minimumStock, Integer maximumStock) {
        log.info("Creating inventory for product: {}", productCode);

        if (initialQuantity < 0 || minimumStock < 0 || maximumStock <= 0) {
            throw new IllegalArgumentException("Invalid stock levels");
        }

        Inventory inventory = Inventory.builder()
                .productCode(productCode)
                .productName(productName)
                .quantity(initialQuantity)
                .minimumStock(minimumStock)
                .maximumStock(maximumStock)
                .status(Inventory.InventoryStatus.IN_STOCK)
                .build();

        return inventoryRepository.save(inventory);
    }

    /**
     * Scenario 8: Concurrent reduction attempts (for testing race conditions)
     * - This method is called multiple times concurrently
     * - With proper locking, only valid reductions succeed
     */
    @Transactional
    public void reduceStockConcurrentSafe(Long inventoryId, Integer quantity) {
        log.info("Reducing stock concurrently safe: {} qty: {}", inventoryId, quantity);

        Inventory inventory = inventoryRepository.findByIdWithWriteLock(inventoryId)
                .orElseThrow(() -> new IllegalArgumentException("Inventory not found"));

        if (inventory.getQuantity() < quantity) {
            throw new OutOfStockException(
                    String.format("Not enough stock. Available: %d, Needed: %d", 
                            inventory.getQuantity(), quantity)
            );
        }

        inventory.setQuantity(inventory.getQuantity() - quantity);
        inventoryRepository.save(inventory);
    }

    /**
     * Helper method: Update inventory quantity with validation
     */
    private void updateInventoryQuantity(Inventory inventory, Integer quantity) {
        int newQuantity = inventory.getQuantity() + quantity;

        if (newQuantity < 0) {
            throw new IllegalArgumentException("Cannot have negative stock");
        }

        if (newQuantity > inventory.getMaximumStock()) {
            throw new IllegalArgumentException("Exceeds maximum stock level");
        }

        inventory.setQuantity(newQuantity);
        inventory.setLastRestockedAt(LocalDateTime.now());
    }

    /**
     * Scenario 9: Get inventory details (read-only)
     */
    @Transactional(readOnly = true)
    public Inventory getInventory(Long inventoryId) {
        return inventoryRepository.findById(inventoryId)
                .orElseThrow(() -> new IllegalArgumentException("Inventory not found"));
    }

    /**
     * Scenario 10: Verify and reduce stock atomically
     * - Combined check and update in single transaction
     */
    @Transactional
    public void verifyAndReduceStock(String productCode, Integer quantity) {
        log.info("Verify and reduce stock for product: {}", productCode);

        Inventory inventory = inventoryRepository.findByProductCode(productCode)
                .orElseThrow(() -> new IllegalArgumentException("Product not found"));

        if (inventory.getStatus() == Inventory.InventoryStatus.DISCONTINUED) {
            throw new IllegalArgumentException("Product is discontinued");
        }

        if (inventory.getQuantity() < quantity) {
            throw new OutOfStockException("Insufficient stock for this product");
        }

        inventory.setQuantity(inventory.getQuantity() - quantity);
        inventoryRepository.save(inventory);
    }
}

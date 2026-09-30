package store.artistictech.consister.service;

import store.artistictech.consister.entity.*;
import store.artistictech.consister.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Order Service - Demonstrates complex nested transactions
 * 
 * Topics covered:
 * 1. Complex multi-entity transactions
 * 2. Cascade operations and orphan removal
 * 3. Nested transactions with different propagation strategies
 * 4. Transaction rollback across related entities
 * 5. Order processing workflow
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OrderService {

    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final InventoryRepository inventoryRepository;
    private final CustomerRepository customerRepository;
    private final InventoryService inventoryService;

    /**
     * Scenario 1: Create order with items (atomic operation)
     * - All items must save or complete transaction rolls back
     * - Demonstrates cascade operations
     */
    @Transactional
    public Order createOrderWithItems(Long customerId, List<OrderItemDTO> itemDTOs) {
        log.info("Creating order for customer: {} with {} items", customerId, itemDTOs.size());

        // Validate customer exists
        Customer customer = customerRepository.findById(customerId)
                .orElseThrow(() -> new IllegalArgumentException("Customer not found"));

        // Create order
        Order order = Order.builder()
                .customerId(customerId)
                .status(Order.OrderStatus.PENDING)
                .totalAmount(BigDecimal.ZERO)
                .build();

        BigDecimal totalAmount = BigDecimal.ZERO;

        // Add items and update inventory
        for (OrderItemDTO itemDTO : itemDTOs) {
            // Verify inventory
            Inventory inventory = inventoryRepository.findByProductCode(itemDTO.getProductCode())
                    .orElseThrow(() -> new IllegalArgumentException("Product not found: " + itemDTO.getProductCode()));

            if (inventory.getQuantity() < itemDTO.getQuantity()) {
                throw new IllegalArgumentException("Insufficient stock for: " + itemDTO.getProductCode());
            }

            // Reduce inventory
            inventoryService.reduceStockWithLock(inventory.getId(), itemDTO.getQuantity());

            // Create order item
            OrderItem item = OrderItem.builder()
                    .order(order)
                    .productCode(itemDTO.getProductCode())
                    .productName(itemDTO.getProductName())
                    .quantity(itemDTO.getQuantity())
                    .unitPrice(itemDTO.getUnitPrice())
                    .totalPrice(itemDTO.getUnitPrice().multiply(new BigDecimal(itemDTO.getQuantity())))
                    .build();

            order.getItems().add(item);
            totalAmount = totalAmount.add(item.getTotalPrice());
        }

        order.setTotalAmount(totalAmount);
        Order savedOrder = orderRepository.save(order);

        log.info("Order created with ID: {} and {} items", savedOrder.getId(), savedOrder.getItems().size());
        return savedOrder;
    }

    /**
     * Scenario 2: Process order (update status with cascading effects)
     */
    @Transactional
    public Order processOrder(Long orderId) {
        log.info("Processing order: {}", orderId);

        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new IllegalArgumentException("Order not found"));

        if (order.getStatus() != Order.OrderStatus.PENDING) {
            throw new IllegalArgumentException("Order must be in PENDING status");
        }

        order.setStatus(Order.OrderStatus.CONFIRMED);
        return orderRepository.save(order);
    }

    /**
     * Scenario 3: Cancel order (with inventory restoration)
     * - Demonstrates compensation transaction
     * - Restores inventory when order is cancelled
     */
    @Transactional
    public void cancelOrder(Long orderId) {
        log.info("Cancelling order: {}", orderId);

        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new IllegalArgumentException("Order not found"));

        if (order.getStatus() == Order.OrderStatus.DELIVERED || order.getStatus() == Order.OrderStatus.CANCELLED) {
            throw new IllegalArgumentException("Cannot cancel order in current status: " + order.getStatus());
        }

        // Restore inventory for all items
        for (OrderItem item : order.getItems()) {
            Inventory inventory = inventoryRepository.findByProductCode(item.getProductCode())
                    .orElseThrow(() -> new IllegalArgumentException("Product not found"));

            inventoryService.restockWithSerialization(inventory.getId(), item.getQuantity());
        }

        order.setStatus(Order.OrderStatus.CANCELLED);
        orderRepository.save(order);

        log.info("Order cancelled and inventory restored");
    }

    /**
     * Scenario 4: Update order (with item management)
     * - Remove old items (orphan removal)
     * - Add new items
     */
    @Transactional
    public Order updateOrderItems(Long orderId, List<OrderItemDTO> newItems) {
        log.info("Updating order: {} with {} new items", orderId, newItems.size());

        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new IllegalArgumentException("Order not found"));

        if (order.getStatus() != Order.OrderStatus.PENDING) {
            throw new IllegalArgumentException("Can only update pending orders");
        }

        // Restore inventory for removed items (not in new list)
        for (OrderItem item : order.getItems()) {
            boolean exists = newItems.stream()
                    .anyMatch(dto -> dto.getProductCode().equals(item.getProductCode()));
            if (!exists) {
                Inventory inventory = inventoryRepository.findByProductCode(item.getProductCode())
                        .orElseThrow(() -> new IllegalArgumentException("Product not found"));
                inventoryService.restockWithSerialization(inventory.getId(), item.getQuantity());
            }
        }

        // Clear items (triggers orphan removal)
        order.getItems().clear();

        // Add new items
        BigDecimal totalAmount = BigDecimal.ZERO;
        for (OrderItemDTO itemDTO : newItems) {
            Inventory inventory = inventoryRepository.findByProductCode(itemDTO.getProductCode())
                    .orElseThrow(() -> new IllegalArgumentException("Product not found"));

            if (inventory.getQuantity() < itemDTO.getQuantity()) {
                throw new IllegalArgumentException("Insufficient stock");
            }

            inventoryService.reduceStockWithLock(inventory.getId(), itemDTO.getQuantity());

            OrderItem item = OrderItem.builder()
                    .order(order)
                    .productCode(itemDTO.getProductCode())
                    .productName(itemDTO.getProductName())
                    .quantity(itemDTO.getQuantity())
                    .unitPrice(itemDTO.getUnitPrice())
                    .totalPrice(itemDTO.getUnitPrice().multiply(new BigDecimal(itemDTO.getQuantity())))
                    .build();

            order.getItems().add(item);
            totalAmount = totalAmount.add(item.getTotalPrice());
        }

        order.setTotalAmount(totalAmount);
        return orderRepository.save(order);
    }

    /**
     * Scenario 5: Ship order
     */
    @Transactional
    public Order shipOrder(Long orderId) {
        log.info("Shipping order: {}", orderId);

        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new IllegalArgumentException("Order not found"));

        if (order.getStatus() != Order.OrderStatus.CONFIRMED) {
            throw new IllegalArgumentException("Only confirmed orders can be shipped");
        }

        order.setStatus(Order.OrderStatus.SHIPPED);
        return orderRepository.save(order);
    }

    /**
     * Scenario 6: Deliver order
     */
    @Transactional
    public Order deliverOrder(Long orderId) {
        log.info("Delivering order: {}", orderId);

        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new IllegalArgumentException("Order not found"));

        if (order.getStatus() != Order.OrderStatus.SHIPPED) {
            throw new IllegalArgumentException("Only shipped orders can be delivered");
        }

        order.setStatus(Order.OrderStatus.DELIVERED);
        return orderRepository.save(order);
    }

    /**
     * Scenario 7: Get orders for customer
     */
    @Transactional(readOnly = true)
    public List<Order> getOrdersByCustomer(Long customerId) {
        log.info("Fetching orders for customer: {}", customerId);
        return orderRepository.findByCustomerId(customerId);
    }

    /**
     * Scenario 8: Get order with items (eager load to avoid lazy loading issues)
     */
    @Transactional(readOnly = true)
    public Order getOrderWithItems(Long orderId) {
        log.info("Fetching order with items: {}", orderId);

        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new IllegalArgumentException("Order not found"));

        // Force load items before transaction ends
        order.getItems().size();

        return order;
    }

    /**
     * DTO for order items
     */
    @lombok.Data
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class OrderItemDTO {
        private String productCode;
        private String productName;
        private Integer quantity;
        private BigDecimal unitPrice;
    }
}

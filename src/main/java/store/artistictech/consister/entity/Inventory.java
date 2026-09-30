package store.artistictech.consister.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

/**
 * Inventory Entity - For stock management scenarios
 * Used in: concurrent modifications, deadlock scenarios, pessimistic locking, isolation levels
 */
@Entity
@Table(name = "inventory", indexes = {
    @Index(name = "idx_product_code", columnList = "product_code", unique = true)
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Inventory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 100)
    private String productCode;

    @Column(nullable = false, length = 255)
    private String productName;

    @Column(nullable = false)
    private Integer quantity;

    @Column(nullable = false)
    private Integer minimumStock;

    @Column(nullable = false)
    private Integer maximumStock;

    @Column(nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    private InventoryStatus status;

    @Column(name = "last_restocked_at")
    private LocalDateTime lastRestockedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Version
    @Column(name = "version")
    private Long version;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        if (status == null) {
            status = InventoryStatus.IN_STOCK;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public enum InventoryStatus {
        IN_STOCK, LOW_STOCK, OUT_OF_STOCK, DISCONTINUED
    }
}

package store.artistictech.consister.entity;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * OrderItem Entity - Items in an order
 * Used in: relationship management, cascading transactions, orphan removal
 */
@Entity
@Table(name = "order_items", indexes = {
    @Index(name = "idx_order_id", columnList = "order_id")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OrderItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id", nullable = false)
    private Order order;

    @Column(nullable = false, length = 100)
    private String productCode;

    @Column(nullable = false, length = 255)
    private String productName;

    @Column(nullable = false)
    private Integer quantity;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal unitPrice;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal totalPrice;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Version
    @Column(name = "version")
    private Long version;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}

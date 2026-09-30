package store.artistictech.consister.entity;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Account Entity - For money transfer and transaction scenarios
 * Used in: rollback on insufficient funds, concurrent modifications, isolation levels
 */
@Entity
@Table(name = "accounts", indexes = {
    @Index(name = "idx_account_number", columnList = "account_number", unique = true)
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Account {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 50)
    private String accountNumber;

    @Column(nullable = false, length = 100)
    private String holderName;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal balance;

    @Column(nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    private AccountStatus status;

    @Column(name = "last_transaction_at")
    private LocalDateTime lastTransactionAt;

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
            status = AccountStatus.ACTIVE;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public enum AccountStatus {
        ACTIVE, INACTIVE, BLOCKED, CLOSED
    }
}

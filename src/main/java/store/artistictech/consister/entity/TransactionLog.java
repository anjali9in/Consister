package store.artistictech.consister.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

/**
 * TransactionLog Entity - For logging and tracking all transactions
 * Used in: audit trails, transaction history, debugging scenarios
 */
@Entity
@Table(name = "transaction_logs", indexes = {
    @Index(name = "idx_account_id", columnList = "account_id"),
    @Index(name = "idx_created_at", columnList = "created_at")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TransactionLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "account_id", nullable = false)
    private Long accountId;

    @Column(nullable = false, length = 50)
    private String transactionType;

    @Column(length = 500)
    private String description;

    @Column(length = 20)
    @Enumerated(EnumType.STRING)
    private TransactionStatus status;

    @Column(length = 500)
    private String errorMessage;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        if (status == null) {
            status = TransactionStatus.PENDING;
        }
    }

    public enum TransactionStatus {
        PENDING, SUCCESS, FAILED, ROLLED_BACK
    }
}

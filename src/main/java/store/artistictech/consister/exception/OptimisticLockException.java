package store.artistictech.consister.exception;

/**
 * Thrown when optimistic locking version conflict occurs
 */
public class OptimisticLockException extends RuntimeException {
    public OptimisticLockException(String message) {
        super(message);
    }

    public OptimisticLockException(String message, Throwable cause) {
        super(message, cause);
    }
}

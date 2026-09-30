package store.artistictech.consister.exception;

/**
 * Thrown when inventory is out of stock
 */
public class OutOfStockException extends RuntimeException {
    public OutOfStockException(String message) {
        super(message);
    }

    public OutOfStockException(String message, Throwable cause) {
        super(message, cause);
    }
}

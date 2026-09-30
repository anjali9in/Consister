package store.artistictech.consister.exception;

/**
 * Thrown when account status is invalid for the operation
 */
public class AccountStatusException extends RuntimeException {
    public AccountStatusException(String message) {
        super(message);
    }

    public AccountStatusException(String message, Throwable cause) {
        super(message, cause);
    }
}

package lt.satsyuk.distributed.audit.command.service;

/**
 * Raised when username/password credentials are invalid.
 */
public class InvalidCredentialsException extends RuntimeException {

    public InvalidCredentialsException(String message) {
        super(message);
    }
}


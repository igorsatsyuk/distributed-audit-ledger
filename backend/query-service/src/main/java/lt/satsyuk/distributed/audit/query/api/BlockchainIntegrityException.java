package lt.satsyuk.distributed.audit.query.api;

public class BlockchainIntegrityException extends RuntimeException {

    public enum ErrorType {
        /**
         * Configuration/validation problem (missing/malformed contract address, invalid hash format, etc.).
         * These are typically programming errors or deployment issues, not transient.
         * HTTP mapping: 500 Internal Server Error (see {@code GlobalExceptionHandler}).
         */
        CONFIGURATION,
        /**
         * Transient RPC connectivity issue (network timeout, node unavailable, etc.).
         * HTTP mapping: 503 Service Unavailable.
         */
        RPC_FAILURE
    }

    private final ErrorType errorType;

    public BlockchainIntegrityException(String message) {
        this(message, ErrorType.RPC_FAILURE);
    }

    public BlockchainIntegrityException(String message, ErrorType errorType) {
        super(message);
        this.errorType = errorType;
    }

    public BlockchainIntegrityException(String message, Throwable cause) {
        this(message, cause, ErrorType.RPC_FAILURE);
    }

    public BlockchainIntegrityException(String message, Throwable cause, ErrorType errorType) {
        super(message, cause);
        this.errorType = errorType;
    }

    public ErrorType getErrorType() {
        return errorType;
    }
}


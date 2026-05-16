package lt.satsyuk.distributed.audit.query.api;

public class BlockchainIntegrityException extends RuntimeException {

    public BlockchainIntegrityException(String message) {
        super(message);
    }

    public BlockchainIntegrityException(String message, Throwable cause) {
        super(message, cause);
    }
}


package lt.satsyuk.distributed.audit.query.service;

/**
 * Thrown when query parameters fail validation.
 * This is a client error, should result in HTTP 400.
 */
public class QueryValidationException extends IllegalArgumentException {
    public QueryValidationException(String message) {
        super(message);
    }
}


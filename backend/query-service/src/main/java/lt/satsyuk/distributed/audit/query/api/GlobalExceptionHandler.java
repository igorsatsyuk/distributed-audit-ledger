package lt.satsyuk.distributed.audit.query.api;

import lt.satsyuk.distributed.audit.query.service.AuditLogNotFoundException;
import lt.satsyuk.distributed.audit.query.service.QueryValidationException;
import lt.satsyuk.distributed.audit.query.service.ReconciliationAlreadyRunningException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ServerWebInputException;

import java.util.Objects;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(AuditLogNotFoundException.class)
    public ResponseEntity<ApiErrorResponse> handleNotFound(AuditLogNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new ApiErrorResponse(ex.getMessage()));
    }

    @ExceptionHandler(QueryValidationException.class)
    public ResponseEntity<ApiErrorResponse> handleQueryValidation(QueryValidationException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ApiErrorResponse(ex.getMessage()));
    }

    @ExceptionHandler(ReconciliationAlreadyRunningException.class)
    public ResponseEntity<ApiErrorResponse> handleReconciliationConflict(ReconciliationAlreadyRunningException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new ApiErrorResponse(ex.getMessage()));
    }

    @ExceptionHandler(BlockchainIntegrityException.class)
    public ResponseEntity<ApiErrorResponse> handleBlockchainIntegrity(BlockchainIntegrityException ex) {
        // Configuration errors (missing/malformed address, invalid hash, etc.) are internal server errors
        if (ex.getErrorType() == BlockchainIntegrityException.ErrorType.CONFIGURATION) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ApiErrorResponse(ex.getMessage()));
        }
        // RPC failures (network issues, timeouts, etc.) are transient service unavailable errors
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(new ApiErrorResponse(ex.getMessage()));
    }

    @ExceptionHandler(ServerWebInputException.class)
    public ResponseEntity<ApiErrorResponse> handleWebInput(ServerWebInputException ex) {
        String message = Objects.requireNonNullElse(ex.getReason(), "Invalid request parameters");
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ApiErrorResponse(message));
    }
}

package lt.satsyuk.distributed.audit.query.api;

import lt.satsyuk.distributed.audit.query.service.AuditLogNotFoundException;
import lt.satsyuk.distributed.audit.query.service.QueryValidationException;
import lt.satsyuk.distributed.audit.query.service.ReconciliationAlreadyRunningException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.server.ServerWebInputException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void mapsNotFoundExceptionTo404() {
        ResponseEntity<ApiErrorResponse> response = handler.handleNotFound(new AuditLogNotFoundException(42L));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().message()).contains("id=42");
    }

    @Test
    void mapsQueryValidationExceptionTo400() {
        ResponseEntity<ApiErrorResponse> response = handler.handleQueryValidation(new QueryValidationException("bad filter"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isEqualTo(new ApiErrorResponse("bad filter"));
    }

    @Test
    void mapsReconciliationConflictTo409() {
        ResponseEntity<ApiErrorResponse> response = handler.handleReconciliationConflict(new ReconciliationAlreadyRunningException());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody()).isEqualTo(new ApiErrorResponse("Reconciliation run is already in progress"));
    }

    @Test
    void mapsConfigurationBlockchainErrorTo500() {
        BlockchainIntegrityException exception = new BlockchainIntegrityException(
                "missing address",
                BlockchainIntegrityException.ErrorType.CONFIGURATION
        );

        ResponseEntity<ApiErrorResponse> response = handler.handleBlockchainIntegrity(exception);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody()).isEqualTo(new ApiErrorResponse("missing address"));
    }

    @Test
    void mapsRpcBlockchainErrorTo503() {
        BlockchainIntegrityException exception = new BlockchainIntegrityException(
                "rpc timeout",
                BlockchainIntegrityException.ErrorType.RPC_FAILURE
        );

        ResponseEntity<ApiErrorResponse> response = handler.handleBlockchainIntegrity(exception);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(response.getBody()).isEqualTo(new ApiErrorResponse("rpc timeout"));
    }

    @Test
    void mapsInputErrorsTo400WithFallbackReason() {
        ResponseEntity<ApiErrorResponse> explicitReason = handler.handleWebInput(new ServerWebInputException("invalid date"));
        ServerWebInputException noReasonException = mock(ServerWebInputException.class);
        when(noReasonException.getReason()).thenReturn(null);
        ResponseEntity<ApiErrorResponse> fallbackReason = handler.handleWebInput(noReasonException);

        assertThat(explicitReason.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(explicitReason.getBody()).isEqualTo(new ApiErrorResponse("invalid date"));

        assertThat(fallbackReason.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(fallbackReason.getBody()).isEqualTo(new ApiErrorResponse("Invalid request parameters"));
    }
}


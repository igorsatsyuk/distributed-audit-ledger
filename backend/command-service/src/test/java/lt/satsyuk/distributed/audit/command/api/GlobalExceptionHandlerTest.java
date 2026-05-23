package lt.satsyuk.distributed.audit.command.api;

import lt.satsyuk.distributed.audit.command.service.CommandPublishException;
import lt.satsyuk.distributed.audit.command.service.InvalidCredentialsException;
import lt.satsyuk.distributed.audit.contracts.dto.CommandResponse;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.support.WebExchangeBindException;
import org.springframework.web.server.ServerWebInputException;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void handleValidationErrorFormatsFieldErrors() {
        WebExchangeBindException exception = mock(WebExchangeBindException.class);
        BindingResult bindingResult = mock(BindingResult.class);
        FieldError blankUserError = new FieldError("authLoginRequest", "username", "must not be blank");
        FieldError fullMessageError = new FieldError("authLoginRequest", "password", "password must not be blank");

        when(exception.getBindingResult()).thenReturn(bindingResult);
        when(bindingResult.getFieldErrors()).thenReturn(List.of(blankUserError, fullMessageError));

        ResponseEntity<CommandResponse> response = handler.handleValidationError(exception);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().isSuccess()).isFalse();
        assertThat(response.getBody().getMessage())
                .isEqualTo("username must not be blank; password must not be blank");
    }

    @Test
    void handleValidationErrorFallsBackWhenDefaultMessageIsNull() {
        WebExchangeBindException exception = mock(WebExchangeBindException.class);
        BindingResult bindingResult = mock(BindingResult.class);
        FieldError fieldError = mock(FieldError.class);

        when(exception.getBindingResult()).thenReturn(bindingResult);
        when(bindingResult.getFieldErrors()).thenReturn(List.of(fieldError));
        when(fieldError.getField()).thenReturn("userId");
        when(fieldError.getDefaultMessage()).thenReturn(null);

        ResponseEntity<CommandResponse> response = handler.handleValidationError(exception);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getMessage()).isEqualTo("userId Validation failed");
    }

    @Test
    void handleWebInputErrorUsesDefaultMessageWhenReasonMissing() {
        ServerWebInputException exception = mock(ServerWebInputException.class);
        when(exception.getReason()).thenReturn(null);

        ResponseEntity<CommandResponse> response = handler.handleWebInputError(exception);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getMessage()).isEqualTo("Invalid request payload");
    }

    @Test
    void handlePublishErrorReturnsServiceUnavailable() {
        CommandPublishException exception = new CommandPublishException("Kafka unavailable", null);

        ResponseEntity<CommandResponse> response = handler.handlePublishError(exception);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().isSuccess()).isFalse();
        assertThat(response.getBody().getMessage()).isEqualTo("Kafka unavailable");
    }

    @Test
    void handleInvalidCredentialsReturnsAuthErrorShape() {
        InvalidCredentialsException exception = new InvalidCredentialsException("Invalid username or password");

        ResponseEntity<AuthErrorResponse> response = handler.handleInvalidCredentials(exception);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().error()).isEqualTo("INVALID_CREDENTIALS");
        assertThat(response.getBody().message()).isEqualTo("Invalid username or password");
    }
}


package lt.satsyuk.distributed.audit.command.api;

import lt.satsyuk.distributed.audit.command.service.CommandPublishException;
import lt.satsyuk.distributed.audit.command.service.InvalidCredentialsException;
import lt.satsyuk.distributed.audit.contracts.dto.CommandResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.bind.support.WebExchangeBindException;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.ServerWebInputException;

import java.util.Objects;

import java.util.stream.Collectors;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(WebExchangeBindException.class)
    public ResponseEntity<Object> handleValidationError(WebExchangeBindException exception,
                                                        ServerWebExchange exchange) {
        String message = exception.getBindingResult()
                .getFieldErrors()
                .stream()
                .map(this::formatFieldError)
                .collect(Collectors.joining("; "));

        return badRequest(exchange, message);
    }

    @ExceptionHandler(ServerWebInputException.class)
    public ResponseEntity<Object> handleWebInputError(ServerWebInputException exception,
                                                      ServerWebExchange exchange) {
        String message = Objects.requireNonNullElse(exception.getReason(), "Invalid request payload");
        return badRequest(exchange, message);
    }

    @ExceptionHandler(CommandPublishException.class)
    public ResponseEntity<CommandResponse> handlePublishError(CommandPublishException exception) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(CommandResponse.rejected(exception.getMessage()));
    }

    @ExceptionHandler(InvalidCredentialsException.class)
    public ResponseEntity<AuthErrorResponse> handleInvalidCredentials(InvalidCredentialsException exception) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(new AuthErrorResponse("INVALID_CREDENTIALS", exception.getMessage()));
    }

    private String formatFieldError(FieldError error) {
        String defaultMessage = error.getDefaultMessage();
        String safeDefaultMessage = Objects.requireNonNullElse(defaultMessage, "Validation failed");
        if (defaultMessage != null && defaultMessage.contains(error.getField())) {
            return safeDefaultMessage;
        }
        return error.getField() + " " + safeDefaultMessage;
    }

    private ResponseEntity<Object> badRequest(ServerWebExchange exchange, String message) {
        if (isAuthRequest(exchange)) {
            return ResponseEntity.badRequest().body(new AuthErrorResponse("INVALID_REQUEST", message));
        }
        return ResponseEntity.badRequest().body(CommandResponse.rejected(message));
    }

    private boolean isAuthRequest(ServerWebExchange exchange) {
        String path = exchange.getRequest().getPath().value();
        return path.startsWith("/auth/");
    }
}


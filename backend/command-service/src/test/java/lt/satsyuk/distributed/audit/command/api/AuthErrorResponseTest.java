package lt.satsyuk.distributed.audit.command.api;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AuthErrorResponseTest {

    @Test
    void authErrorResponseStoresErrorAndMessage() {
        String error = "INVALID_CREDENTIALS";
        String message = "Invalid username or password";

        AuthErrorResponse response = new AuthErrorResponse(error, message);

        assertThat(response.error()).isEqualTo(error);
        assertThat(response.message()).isEqualTo(message);
    }

    @Test
    void authErrorResponseCanBeDecomposed() {
        AuthErrorResponse response = new AuthErrorResponse("UNAUTHORIZED", "Access denied");
        String error = response.error();
        String message = response.message();

        assertThat(error).isEqualTo("UNAUTHORIZED");
        assertThat(message).isEqualTo("Access denied");
    }
}


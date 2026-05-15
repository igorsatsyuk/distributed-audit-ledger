package lt.satsyuk.distributed.audit.contracts.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Generic API response envelope returned by command endpoints.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CommandResponse {

    /** Whether the command was accepted successfully. */
    private boolean success;

    /** Human-readable message (confirmation or error description). */
    private String message;

    /** The event ID generated for the accepted command (nullable on error). */
    private String eventId;

    // ----- convenience factories ------------------------------------

    public static CommandResponse accepted(String eventId) {
        return CommandResponse.builder()
                .success(true)
                .message("Command accepted")
                .eventId(eventId)
                .build();
    }

    public static CommandResponse rejected(String reason) {
        return CommandResponse.builder()
                .success(false)
                .message(reason)
                .build();
    }
}


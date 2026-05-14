package lt.satsyuk.distributed.audit.event;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

/**
 * Domain event raised when a user successfully logs in.
 *
 * <p>Published to Kafka topic {@code user.login.events} by the command-service
 * and consumed by event-store-service and audit-writer-service.
 */
@Data
@SuperBuilder
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class UserLoggedInEvent extends AuditEvent {

    /** Identifier of the user who logged in. */
    private String userId;

    /** IP address of the client (optional, may be null). */
    private String ipAddress;

    /** User-agent string of the client (optional, may be null). */
    private String userAgent;

    /**
     * Factory method — creates a fully initialised event.
     *
     * @param userId    the authenticated user's identifier
     * @param ipAddress originating IP (nullable)
     * @param userAgent HTTP User-Agent header value (nullable)
     * @return a ready-to-publish event
     */
    public static UserLoggedInEvent of(String userId, String ipAddress, String userAgent) {
        UserLoggedInEvent event = UserLoggedInEvent.builder()
                .userId(userId)
                .ipAddress(ipAddress)
                .userAgent(userAgent)
                .build();
        event.initDefaults(EventType.USER_LOGGED_IN, "command-service");
        return event;
    }
}


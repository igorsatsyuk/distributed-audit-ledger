package lt.satsyuk.distributed.audit.command.service;

/**
 * Raised when a command cannot be published to Kafka.
 */
public class CommandPublishException extends RuntimeException {

    public CommandPublishException(String message, Throwable cause) {
        super(message, cause);
    }
}


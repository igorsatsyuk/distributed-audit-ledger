package lt.satsyuk.distributed.audit.auditwriter.consumer;

import lt.satsyuk.distributed.audit.auditwriter.service.BlockchainWriterService;
import lt.satsyuk.distributed.audit.event.AuditEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

/**
 * Kafka consumer that subscribes to {@code user.login.events} and delegates
 * each incoming {@link AuditEvent} to {@link BlockchainWriterService} for
 * on-chain anchoring.
 *
 * <p>Consumer group {@code audit-writer-consumer} runs independently of
 * {@code event-store-service} so both services process the same events without
 * competing (both have their own offsets).
 */
@Component
public class AuditEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(AuditEventConsumer.class);

    private final BlockchainWriterService blockchainWriterService;

    public AuditEventConsumer(BlockchainWriterService blockchainWriterService) {
        this.blockchainWriterService = blockchainWriterService;
    }

    /**
     * Handles a single {@link AuditEvent} message from Kafka.
     *
     * <p>Errors thrown by {@link BlockchainWriterService} are caught and logged
     * so that a single bad event does not stall partition processing.
     *
     * @param event     the deserialised domain event
     * @param partition Kafka partition (for structured logging)
     * @param offset    Kafka offset (for structured logging)
     */
    @KafkaListener(
            topics = "${kafka.topics.user-login-events}",
            groupId = "${spring.kafka.consumer.group-id}"
    )
    public void consume(
            @Payload AuditEvent event,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset) {

        log.debug("[#7] Received event id={} type={} partition={} offset={}",
                event.getEventId(), event.getEventType(), partition, offset);

        try {
            blockchainWriterService.anchorEvent(event);
        } catch (BlockchainWriterService.BlockchainWriteException e) {
            // Log and continue — do NOT rethrow, to avoid endless Kafka redelivery
            // of events that the contract genuinely cannot accept.
            log.error("[#7] Blockchain anchor failed for event {} — skipping after retries exhausted",
                    event.getEventId(), e);
        } catch (Exception e) {
            log.error("[#7] Unexpected error processing event {} — skipping", event.getEventId(), e);
        }
    }
}


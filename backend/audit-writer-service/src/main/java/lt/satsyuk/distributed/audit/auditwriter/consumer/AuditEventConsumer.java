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
 *
 * <p>Failures are intentionally allowed to propagate to the Kafka container so
 * the configured error handler can apply central retry policy. Recoverable write
 * failures and non-recoverable event payload failures are eventually routed to the
 * DLT, while {@code BlockchainNotConfiguredException} and
 * {@code ReceiptTimeoutException} are re-thrown to keep the source offset
 * uncommitted until configuration is fixed or in-flight transaction outcome
 * becomes known.
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
     * <p>Exceptions are not swallowed here; they are handled by the listener
     * container's {@link org.springframework.kafka.listener.CommonErrorHandler},
     * which applies retry/DLT policy centrally.
     *
     * <p>A Kafka tombstone (null value) is treated as a non-recoverable event.
     *
     * @param event     the deserialised domain event (nullable for tombstones)
     * @param partition Kafka partition (for structured logging)
     * @param offset    Kafka offset (for structured logging)
     * @throws BlockchainWriterService.NonRecoverableEventException if event is null (tombstone)
     */
    @KafkaListener(
            topics = "${kafka.topics.user-login-events}",
            groupId = "${spring.kafka.consumer.group-id}"
    )
    public void consume(
            @Payload(required = false) AuditEvent event,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset) {

        if (event == null) {
            throw new BlockchainWriterService.NonRecoverableEventException(
                    "Received Kafka tombstone (null value) on partition=" + partition + " offset=" + offset);
        }

        log.debug("[#7] Received event id={} type={} partition={} offset={}",
                event.getEventId(), event.getEventType(), partition, offset);

        blockchainWriterService.anchorEvent(event);
    }
}

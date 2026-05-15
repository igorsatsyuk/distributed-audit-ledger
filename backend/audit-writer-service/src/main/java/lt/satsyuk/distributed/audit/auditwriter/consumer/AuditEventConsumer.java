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
     * <p>Failures are propagated back to the container so Kafka retries the
     * record instead of committing an event that was not anchored on-chain.
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

        blockchainWriterService.anchorEvent(event);
    }
}


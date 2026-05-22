package lt.satsyuk.distributed.audit.command.service;

import lt.satsyuk.distributed.audit.command.config.KafkaTopicsProperties;
import lt.satsyuk.distributed.audit.command.repository.InMemoryEventStorage;
import lt.satsyuk.distributed.audit.contracts.dto.CommandResponse;
import lt.satsyuk.distributed.audit.event.AuditEvent;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

/**
 * Shared Kafka publisher for accepted audit commands.
 */
@Service
public class AuditCommandPublisher {

    private final KafkaTemplate<String, AuditEvent> kafkaTemplate;
    private final KafkaTopicsProperties kafkaTopicsProperties;
    private final InMemoryEventStorage inMemoryEventStorage;

    public AuditCommandPublisher(
            KafkaTemplate<String, AuditEvent> kafkaTemplate,
            KafkaTopicsProperties kafkaTopicsProperties,
            InMemoryEventStorage inMemoryEventStorage
    ) {
        this.kafkaTemplate = kafkaTemplate;
        this.kafkaTopicsProperties = kafkaTopicsProperties;
        this.inMemoryEventStorage = inMemoryEventStorage;
    }

    public Mono<CommandResponse> publish(AuditEvent event) {
        return Mono.defer(() -> Mono.fromFuture(kafkaTemplate.send(
                                kafkaTopicsProperties.getAuditEvents(),
                                event.getEventId(),
                                event
                        )))
                .doOnNext(ignored -> inMemoryEventStorage.save(event))
                .map(ignored -> CommandResponse.accepted(event.getEventId()))
                .onErrorMap(error -> new CommandPublishException("Failed to publish event to Kafka", error));
    }
}

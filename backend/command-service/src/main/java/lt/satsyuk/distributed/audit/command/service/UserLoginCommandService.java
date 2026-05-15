package lt.satsyuk.distributed.audit.command.service;

import lt.satsyuk.distributed.audit.command.config.KafkaTopicsProperties;
import lt.satsyuk.distributed.audit.command.repository.InMemoryEventStorage;
import lt.satsyuk.distributed.audit.contracts.command.UserLoginCommand;
import lt.satsyuk.distributed.audit.contracts.dto.CommandResponse;
import lt.satsyuk.distributed.audit.event.AuditEvent;
import lt.satsyuk.distributed.audit.event.UserLoggedInEvent;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

@Service
public class UserLoginCommandService {

    private final KafkaTemplate<String, AuditEvent> kafkaTemplate;
    private final KafkaTopicsProperties kafkaTopicsProperties;
    private final InMemoryEventStorage inMemoryEventStorage;

    public UserLoginCommandService(
            KafkaTemplate<String, AuditEvent> kafkaTemplate,
            KafkaTopicsProperties kafkaTopicsProperties,
            InMemoryEventStorage inMemoryEventStorage
    ) {
        this.kafkaTemplate = kafkaTemplate;
        this.kafkaTopicsProperties = kafkaTopicsProperties;
        this.inMemoryEventStorage = inMemoryEventStorage;
    }

    public Mono<CommandResponse> handleUserLogin(UserLoginCommand command, String requestIp, String requestUserAgent) {
        // Prefer server-derived metadata over client-supplied body values to prevent spoofing.
        String effectiveIp = StringUtils.hasText(requestIp) ? requestIp : command.getIpAddress();
        String effectiveUserAgent = StringUtils.hasText(requestUserAgent)
                ? requestUserAgent
                : command.getUserAgent();

        UserLoggedInEvent event = UserLoggedInEvent.of(command.getUserId(), effectiveIp, effectiveUserAgent);

        return Mono.defer(() -> Mono.fromFuture(kafkaTemplate.send(
                                kafkaTopicsProperties.getUserLoginEvents(),
                                event.getEventId(),
                                event
                        )))
                .subscribeOn(Schedulers.boundedElastic())
                .publishOn(Schedulers.boundedElastic())
                .doOnNext(ignored -> inMemoryEventStorage.save(event))
                .map(ignored -> CommandResponse.accepted(event.getEventId()))
                .onErrorMap(error -> new CommandPublishException("Failed to publish event to Kafka", error));
    }
}


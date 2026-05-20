package lt.satsyuk.distributed.audit.command.service;

import lt.satsyuk.distributed.audit.command.config.KafkaTopicsProperties;
import lt.satsyuk.distributed.audit.command.repository.InMemoryEventStorage;
import lt.satsyuk.distributed.audit.contracts.command.DataDeletedCommand;
import lt.satsyuk.distributed.audit.contracts.command.EntityCreatedCommand;
import lt.satsyuk.distributed.audit.contracts.command.EntityUpdatedCommand;
import lt.satsyuk.distributed.audit.contracts.command.UserProfileChangeCommand;
import lt.satsyuk.distributed.audit.contracts.dto.CommandResponse;
import lt.satsyuk.distributed.audit.event.AuditEvent;
import lt.satsyuk.distributed.audit.event.DataDeletedEvent;
import lt.satsyuk.distributed.audit.event.EntityCreatedEvent;
import lt.satsyuk.distributed.audit.event.EntityUpdatedEvent;
import lt.satsyuk.distributed.audit.event.UserProfileChangedEvent;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

@Service
public class AdditionalCommandService {

	private final KafkaTemplate<String, AuditEvent> kafkaTemplate;
	private final KafkaTopicsProperties kafkaTopicsProperties;
	private final InMemoryEventStorage inMemoryEventStorage;

	public AdditionalCommandService(
			KafkaTemplate<String, AuditEvent> kafkaTemplate,
			KafkaTopicsProperties kafkaTopicsProperties,
			InMemoryEventStorage inMemoryEventStorage
	) {
		this.kafkaTemplate = kafkaTemplate;
		this.kafkaTopicsProperties = kafkaTopicsProperties;
		this.inMemoryEventStorage = inMemoryEventStorage;
	}

	public Mono<CommandResponse> handleUserProfileChange(UserProfileChangeCommand command) {
		return publish(UserProfileChangedEvent.of(command.getUserId(), command.getChangedFields()));
	}

	public Mono<CommandResponse> handleEntityCreated(EntityCreatedCommand command) {
		return publish(EntityCreatedEvent.of(
				command.getUserId(),
				command.getEntityType(),
				command.getEntityId(),
				command.getEntityData()
		));
	}

	public Mono<CommandResponse> handleEntityUpdated(EntityUpdatedCommand command) {
		return publish(EntityUpdatedEvent.of(
				command.getUserId(),
				command.getEntityType(),
				command.getEntityId(),
				command.getChangedFields()
		));
	}

	public Mono<CommandResponse> handleDataDeleted(DataDeletedCommand command) {
		return publish(DataDeletedEvent.of(
				command.getUserId(),
				command.getEntityType(),
				command.getEntityId(),
				command.getReason()
		));
	}

	private Mono<CommandResponse> publish(AuditEvent event) {
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


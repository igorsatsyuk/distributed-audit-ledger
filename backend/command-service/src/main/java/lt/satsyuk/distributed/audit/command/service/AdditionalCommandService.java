package lt.satsyuk.distributed.audit.command.service;

import lt.satsyuk.distributed.audit.contracts.command.DataDeletedCommand;
import lt.satsyuk.distributed.audit.contracts.command.EntityCreatedCommand;
import lt.satsyuk.distributed.audit.contracts.command.EntityUpdatedCommand;
import lt.satsyuk.distributed.audit.contracts.command.UserProfileChangeCommand;
import lt.satsyuk.distributed.audit.contracts.dto.CommandResponse;
import lt.satsyuk.distributed.audit.event.DataDeletedEvent;
import lt.satsyuk.distributed.audit.event.EntityCreatedEvent;
import lt.satsyuk.distributed.audit.event.EntityUpdatedEvent;
import lt.satsyuk.distributed.audit.event.UserProfileChangedEvent;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
public class AdditionalCommandService {

    private final AuditCommandPublisher auditCommandPublisher;

    public AdditionalCommandService(AuditCommandPublisher auditCommandPublisher) {
        this.auditCommandPublisher = auditCommandPublisher;
    }

    public Mono<CommandResponse> handleUserProfileChange(UserProfileChangeCommand command) {
        return auditCommandPublisher.publish(UserProfileChangedEvent.of(command.getUserId(), command.getChangedFields()));
    }

    public Mono<CommandResponse> handleEntityCreated(EntityCreatedCommand command) {
        return auditCommandPublisher.publish(EntityCreatedEvent.of(
                command.getUserId(),
                command.getEntityType(),
                command.getEntityId(),
                command.getEntityData()
        ));
    }

    public Mono<CommandResponse> handleEntityUpdated(EntityUpdatedCommand command) {
        return auditCommandPublisher.publish(EntityUpdatedEvent.of(
                command.getUserId(),
                command.getEntityType(),
                command.getEntityId(),
                command.getChangedFields()
        ));
    }

    public Mono<CommandResponse> handleDataDeleted(DataDeletedCommand command) {
        return auditCommandPublisher.publish(DataDeletedEvent.of(
                command.getUserId(),
                command.getEntityType(),
                command.getEntityId(),
                command.getReason()
        ));
    }
}


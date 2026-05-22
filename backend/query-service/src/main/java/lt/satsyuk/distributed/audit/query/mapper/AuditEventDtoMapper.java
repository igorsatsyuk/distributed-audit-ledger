package lt.satsyuk.distributed.audit.query.mapper;

import lt.satsyuk.distributed.audit.contracts.dto.AuditEventDto;
import lt.satsyuk.distributed.audit.event.EventType;
import lt.satsyuk.distributed.audit.query.model.AuditEventRecord;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

@Mapper(componentModel = "spring")
public interface AuditEventDtoMapper {

    @Mapping(target = "eventType", expression = "java(toEventType(entity.getEventType()))")
    @Mapping(target = "occurredAt", expression = "java(toInstant(entity.getCreatedAt()))")
    @Mapping(target = "eventDataJson", source = "payload")
    @Mapping(target = "integrityStatus", expression = "java(defaultIntegrityStatus())")
    AuditEventDto toDto(AuditEventRecord entity);

    default EventType toEventType(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return EventType.valueOf(value);
        } catch (IllegalArgumentException _) {
            // Return null for unknown event types to avoid breaking the whole request
            // when query-service is deployed behind event-store with a newer enum
            return null;
        }
    }

    default Instant toInstant(LocalDateTime value) {
        if (value == null) {
            return null;
        }
        return value.toInstant(ZoneOffset.UTC);
    }

    default String defaultIntegrityStatus() {
        return "PENDING";
    }
}

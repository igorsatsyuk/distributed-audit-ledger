package lt.satsyuk.distributed.audit.query.mapper;

import lt.satsyuk.distributed.audit.contracts.dto.AuditEventDto;
import lt.satsyuk.distributed.audit.event.EventType;
import lt.satsyuk.distributed.audit.query.model.AuditEventRecord;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AuditEventDtoMapperTest {

    private final AuditEventDtoMapper mapper = Mappers.getMapper(AuditEventDtoMapper.class);

    @Test
    void toDtoMapsCoreFields() {
        AuditEventRecord eventRecord = new AuditEventRecord();
        eventRecord.setId(15L);
        eventRecord.setEventId("event-15");
        eventRecord.setEventType("USER_LOGGED_IN");
        eventRecord.setUserId("user-15");
        eventRecord.setPayload("{\"userId\":\"user-15\"}");
        eventRecord.setEventHash("abc");
        eventRecord.setCreatedAt(LocalDateTime.of(2026, 5, 15, 10, 30, 0));

        AuditEventDto dto = mapper.toDto(eventRecord);

        assertEquals(15L, dto.getId());
        assertEquals("event-15", dto.getEventId());
        assertEquals(EventType.USER_LOGGED_IN, dto.getEventType());
        assertEquals("user-15", dto.getUserId());
        assertEquals("{\"userId\":\"user-15\"}", dto.getEventDataJson());
        assertEquals("PENDING", dto.getIntegrityStatus());
        assertEquals("2026-05-15T10:30:00Z", dto.getOccurredAt().toString());
    }
}

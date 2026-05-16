package lt.satsyuk.distributed.audit.query.service;

import lt.satsyuk.distributed.audit.contracts.dto.AuditEventDto;
import lt.satsyuk.distributed.audit.event.EventType;
import lt.satsyuk.distributed.audit.query.mapper.AuditEventDtoMapper;
import lt.satsyuk.distributed.audit.query.model.AuditEventRecord;
import lt.satsyuk.distributed.audit.query.repository.AuditLogQueryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuditLogQueryServiceTest {

    @Mock
    private AuditLogQueryRepository auditLogQueryRepository;

    @Mock
    private AuditEventDtoMapper mapper;

    private AuditLogQueryService service;

    @BeforeEach
    void setUp() {
        service = new AuditLogQueryService(auditLogQueryRepository, mapper);
    }

    @Test
    void findAuditLogsAppliesFilterAndMapsRecords() {
        AuditEventRecord eventRecord = sampleRecord(101L, "user-1");
        AuditEventDto dto = AuditEventDto.builder().id(101L).userId("user-1").build();

        when(auditLogQueryRepository.findByFilter(any())).thenReturn(Flux.just(eventRecord));
        when(mapper.toDto(eventRecord)).thenReturn(dto);

        AuditEventDto result = service.findAuditLogs(
                        "user-1",
                        EventType.USER_LOGGED_IN,
                        Instant.parse("2026-05-01T00:00:00Z"),
                        Instant.parse("2026-05-31T23:59:59Z")
                )
                .blockFirst();

        assertEquals(dto, result);

        ArgumentCaptor<AuditLogFilter> filterCaptor = ArgumentCaptor.forClass(AuditLogFilter.class);
        verify(auditLogQueryRepository).findByFilter(filterCaptor.capture());
        assertEquals("user-1", filterCaptor.getValue().userId());
        assertEquals(EventType.USER_LOGGED_IN, filterCaptor.getValue().eventType());
    }

    @Test
    void findAuditLogsRejectsInvalidRange() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> service.findAuditLogs(
                        null,
                        null,
                        Instant.parse("2026-05-20T00:00:00Z"),
                        Instant.parse("2026-05-10T00:00:00Z")
                ));

        assertEquals("Query parameter 'from' must be before or equal to 'to'", exception.getMessage());
    }

    @Test
    void findByIdThrowsNotFoundWhenRecordMissing() {
        when(auditLogQueryRepository.findById(404L)).thenReturn(Mono.empty());

        assertThrows(AuditLogNotFoundException.class, () -> service.findById(404L).block());
    }

    private AuditEventRecord sampleRecord(Long id, String userId) {
        AuditEventRecord eventRecord = new AuditEventRecord();
        eventRecord.setId(id);
        eventRecord.setEventId("event-" + id);
        eventRecord.setEventType("USER_LOGGED_IN");
        eventRecord.setUserId(userId);
        eventRecord.setCreatedAt(LocalDateTime.of(2026, 5, 16, 10, 0));
        eventRecord.setPayload("{\"userId\":\"" + userId + "\"}");
        return eventRecord;
    }
}

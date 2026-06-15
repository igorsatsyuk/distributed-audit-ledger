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
import java.time.Month;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
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
        AuditEventRecord eventRecord = sampleRecord();
        // Mapper contract test only needs identity equality, not DTO field mutation.
        AuditEventDto dto = mock(AuditEventDto.class);

        when(auditLogQueryRepository.findByFilter(any())).thenReturn(Flux.just(eventRecord));
        when(mapper.toDto(eventRecord)).thenReturn(dto);

        AuditEventDto result = service.findAuditLogs(
                        "user-1",
                        EventType.USER_LOGGED_IN,
                        Instant.parse("2026-05-01T00:00:00Z"),
                        Instant.parse("2026-05-31T23:59:59Z"),
                        "10.0.0.5",
                        null,
                        null
                )
                .blockFirst();

        assertNotNull(result);
        assertEquals(dto, result);

        ArgumentCaptor<AuditLogFilter> filterCaptor = ArgumentCaptor.forClass(AuditLogFilter.class);
        verify(auditLogQueryRepository).findByFilter(filterCaptor.capture());
        assertEquals("user-1", filterCaptor.getValue().userId());
        assertEquals(EventType.USER_LOGGED_IN, filterCaptor.getValue().eventType());
        assertEquals("10.0.0.5", filterCaptor.getValue().search());
        assertEquals(100, filterCaptor.getValue().limit());
        assertEquals(0L, filterCaptor.getValue().offset());
    }

    @Test
    void findAuditLogsNormalizesBlankSearchToNull() {
        when(auditLogQueryRepository.findByFilter(any())).thenReturn(Flux.empty());

        service.findAuditLogs(null, null, null, null, "   ", null, null).blockFirst();

        ArgumentCaptor<AuditLogFilter> filterCaptor = ArgumentCaptor.forClass(AuditLogFilter.class);
        verify(auditLogQueryRepository).findByFilter(filterCaptor.capture());
        assertNull(filterCaptor.getValue().search());
    }

    @Test
    void findAuditLogsRejectsInvalidRange() {
        Instant from = Instant.parse("2026-05-20T00:00:00Z");
        Instant to = Instant.parse("2026-05-10T00:00:00Z");

        assertThrows(QueryValidationException.class, () -> blockAuditLogsWithRange(from, to));
    }

    @Test
    void findAuditLogsRejectsNegativeLimit() {
        assertThrows(QueryValidationException.class, () -> blockAuditLogsWithLimit(-1));
    }

    @Test
    void findAuditLogsRejectsZeroLimit() {
        assertThrows(QueryValidationException.class, () -> blockAuditLogsWithLimit(0));
    }

    @Test
    void findAuditLogsRejectsNegativeOffset() {
        assertThrows(QueryValidationException.class, this::blockAuditLogsWithNegativeOffset);
    }

    @Test
    void findAuditLogsRejectsLimitAboveMax() {
        assertThrows(QueryValidationException.class, () -> blockAuditLogsWithLimit(1000));
    }

    @Test
    void findByIdThrowsNotFoundWhenRecordMissing() {
        when(auditLogQueryRepository.findById(404L)).thenReturn(Mono.empty());

        assertThrows(AuditLogNotFoundException.class, this::blockMissingAuditLogById);
    }

    private AuditEventRecord sampleRecord() {
        AuditEventRecord eventRecord = new AuditEventRecord();
        eventRecord.setId(101L);
        eventRecord.setEventId("event-101");
        eventRecord.setEventType("USER_LOGGED_IN");
        eventRecord.setUserId("user-1");
        eventRecord.setCreatedAt(LocalDateTime.of(2026, Month.MAY, 16, 10, 0));
        eventRecord.setPayload("{\"userId\":\"user-1\"}");
        return eventRecord;
    }

    private void blockAuditLogsWithRange(Instant from, Instant to) {
        service.findAuditLogs(null, null, from, to, null, 10, 0L).blockFirst();
    }

    private void blockAuditLogsWithLimit(int limit) {
        service.findAuditLogs(null, null, null, null, null, limit, 0L).blockFirst();
    }

    private void blockAuditLogsWithNegativeOffset() {
        service.findAuditLogs(null, null, null, null, null, 100, -1L).blockFirst();
    }

    private void blockMissingAuditLogById() {
        service.findById(404L).block();
    }

}

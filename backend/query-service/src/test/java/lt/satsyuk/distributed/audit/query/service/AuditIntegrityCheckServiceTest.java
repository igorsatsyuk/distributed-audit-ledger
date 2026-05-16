package lt.satsyuk.distributed.audit.query.service;

import lt.satsyuk.distributed.audit.query.api.AuditIntegrityCheckResponse;
import lt.satsyuk.distributed.audit.query.blockchain.AuditLedgerBlockchainClient;
import lt.satsyuk.distributed.audit.query.model.AuditEventRecord;
import lt.satsyuk.distributed.audit.query.repository.AuditLogQueryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuditIntegrityCheckServiceTest {

    private static final String HASH_64 = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";

    @Mock
    private AuditLogQueryRepository auditLogQueryRepository;

    @Mock
    private AuditLedgerBlockchainClient blockchainClient;

    private AuditIntegrityCheckService service;

    @BeforeEach
    void setUp() {
        service = new AuditIntegrityCheckService(auditLogQueryRepository, blockchainClient);
    }

    @Test
    void checkIntegrityReturnsOkWhenHashIsAnchored() {
        AuditEventRecord eventRecord = sampleRecord(10L, HASH_64);
        AuditIntegrityCheckResponse.BlockchainRecord blockchainRecord =
                new AuditIntegrityCheckResponse.BlockchainRecord(true, "0xabc", 42L, 1715774400L);

        when(auditLogQueryRepository.findById(10L)).thenReturn(Mono.just(eventRecord));
        when(blockchainClient.inspectEventHash(HASH_64)).thenReturn(Mono.just(blockchainRecord));

        AuditIntegrityCheckResponse result = service.checkIntegrity(10L).block();

        assertEquals(10L, result.auditLogId());
        assertEquals("event-10", result.eventId());
        assertEquals(HASH_64, result.eventHash());
        assertEquals(blockchainRecord, result.blockchainRecord());
        assertEquals("ON_CHAIN", result.status());
    }

    @Test
    void checkIntegrityReturnsMismatchWhenHashIsNotOnChain() {
        AuditEventRecord eventRecord = sampleRecord(11L, HASH_64);
        AuditIntegrityCheckResponse.BlockchainRecord blockchainRecord =
                new AuditIntegrityCheckResponse.BlockchainRecord(false, null, null, null);

        when(auditLogQueryRepository.findById(11L)).thenReturn(Mono.just(eventRecord));
        when(blockchainClient.inspectEventHash(HASH_64)).thenReturn(Mono.just(blockchainRecord));

        AuditIntegrityCheckResponse result = service.checkIntegrity(11L).block();

        assertEquals("MISMATCH", result.status());
        assertEquals(blockchainRecord, result.blockchainRecord());
    }

    @Test
    void checkIntegrityReturnsMismatchWhenDbHashMissing() {
        AuditEventRecord eventRecord = sampleRecord(12L, null);

        when(auditLogQueryRepository.findById(12L)).thenReturn(Mono.just(eventRecord));

        AuditIntegrityCheckResponse result = service.checkIntegrity(12L).block();

        assertEquals(12L, result.auditLogId());
        assertEquals("event-12", result.eventId());
        assertNull(result.eventHash());
        assertFalse(result.blockchainRecord().exists());
        assertEquals("PENDING", result.status());
        verify(blockchainClient, never()).inspectEventHash(HASH_64);
    }

    @Test
    void checkIntegrityThrowsNotFoundWhenRecordMissing() {
        when(auditLogQueryRepository.findById(404L)).thenReturn(Mono.empty());

        assertThrows(AuditLogNotFoundException.class, () -> service.checkIntegrity(404L).block());
    }

    private AuditEventRecord sampleRecord(Long id, String eventHash) {
        AuditEventRecord eventRecord = new AuditEventRecord();
        eventRecord.setId(id);
        eventRecord.setEventId("event-" + id);
        eventRecord.setEventType("USER_LOGGED_IN");
        eventRecord.setUserId("user-" + id);
        eventRecord.setEventHash(eventHash);
        return eventRecord;
    }
}


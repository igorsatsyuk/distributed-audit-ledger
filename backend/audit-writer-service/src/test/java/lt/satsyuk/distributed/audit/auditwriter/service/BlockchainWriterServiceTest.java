package lt.satsyuk.distributed.audit.auditwriter.service;

import lt.satsyuk.distributed.audit.auditwriter.blockchain.AuditLedgerContract;
import lt.satsyuk.distributed.audit.auditwriter.config.JacksonConfig;
import lt.satsyuk.distributed.audit.auditwriter.config.Web3jProperties;
import lt.satsyuk.distributed.audit.event.EventType;
import lt.satsyuk.distributed.audit.event.UserLoggedInEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.web3j.crypto.Credentials;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.methods.response.TransactionReceipt;

import java.math.BigInteger;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link BlockchainWriterService}.
 *
 * <p>The {@link AuditLedgerContract} is mocked via {@link MockedStatic} so no
 * live Ganache node is required.
 */
@ExtendWith(MockitoExtension.class)
class BlockchainWriterServiceTest {

    @Mock private Web3j web3j;
    @Mock private Credentials credentials;
    @Mock private AuditLedgerContract contract;

    private Web3jProperties props;
    private HashCalculationService hashService;
    private BlockchainWriterService service;

    @BeforeEach
    void setUp() {
        props = new Web3jProperties();
        props.setClientAddress("http://localhost:8545");
        props.setContractAddress("0xdeadbeef");
        props.setPrivateKey("0x1234");

        hashService = new HashCalculationService(new JacksonConfig().objectMapper());

        lenient().when(credentials.getAddress()).thenReturn("0xsender");

        service = new BlockchainWriterService(web3j, credentials, props, hashService, 0L);
    }

    @Test
    void anchorEvent_failsWhenContractAddressBlank() {
        props.setContractAddress("");
        UserLoggedInEvent event = UserLoggedInEvent.of("u1", null, null);

        assertThatThrownBy(() -> service.anchorEvent(event))
                .isInstanceOf(BlockchainWriterService.BlockchainWriteException.class)
                .hasMessageContaining("not configured");
    }

    @Test
    void anchorEvent_failsWhenCredentialsNull() {
        BlockchainWriterService noCredService =
                new BlockchainWriterService(web3j, null, props, hashService, 0L);
        UserLoggedInEvent event = UserLoggedInEvent.of("u1", null, null);

        assertThatThrownBy(() -> noCredService.anchorEvent(event))
                .isInstanceOf(BlockchainWriterService.BlockchainWriteException.class)
                .hasMessageContaining("not configured");
    }

    @Test
    void anchorEvent_successOnFirstAttempt() throws Exception {
        UserLoggedInEvent event = UserLoggedInEvent.of("u1", "1.2.3.4", null);
        TransactionReceipt receipt = new TransactionReceipt();
        receipt.setTransactionHash("0xabc");
        receipt.setBlockNumber("0x1");

        when(contract.isHashExists(any())).thenReturn(false);
        when(contract.appendAuditRecord(any(), any(BigInteger.class), anyString(), anyString()))
                .thenReturn(receipt);

        try (MockedStatic<AuditLedgerContract> mocked = mockStatic(AuditLedgerContract.class)) {
            mocked.when(() -> AuditLedgerContract.load(anyString(), any(), any(), any()))
                    .thenReturn(contract);

            assertThatCode(() -> service.anchorEvent(event)).doesNotThrowAnyException();
        }

        verify(contract).appendAuditRecord(any(), any(BigInteger.class), eq("USER_LOGGED_IN"), eq("0xsender"));
    }

    @Test
    void anchorEvent_treatsExistingHashAsSuccess() throws Exception {
        UserLoggedInEvent event = UserLoggedInEvent.of("u1", null, null);

        when(contract.isHashExists(any())).thenReturn(true);

        try (MockedStatic<AuditLedgerContract> mocked = mockStatic(AuditLedgerContract.class)) {
            mocked.when(() -> AuditLedgerContract.load(anyString(), any(), any(), any()))
                    .thenReturn(contract);

            assertThatCode(() -> service.anchorEvent(event)).doesNotThrowAnyException();
        }

        verify(contract, never()).appendAuditRecord(any(), any(), any(), any());
    }

    @Test
    void anchorEvent_treatsDuplicateHashContractRevertAsSuccess() throws Exception {
        UserLoggedInEvent event = UserLoggedInEvent.of("u1", null, null);

        when(contract.isHashExists(any())).thenReturn(false);
        when(contract.appendAuditRecord(any(), any(BigInteger.class), anyString(), anyString()))
                .thenThrow(new RuntimeException("execution reverted: DuplicateHash"));

        try (MockedStatic<AuditLedgerContract> mocked = mockStatic(AuditLedgerContract.class)) {
            mocked.when(() -> AuditLedgerContract.load(anyString(), any(), any(), any()))
                    .thenReturn(contract);

            assertThatCode(() -> service.anchorEvent(event)).doesNotThrowAnyException();
        }
    }

    @Test
    void anchorEvent_throwsBlockchainWriteExceptionAfterAllRetriesExhausted() throws Exception {
        UserLoggedInEvent event = UserLoggedInEvent.of("u1", null, null);

        when(contract.isHashExists(any())).thenThrow(new RuntimeException("RPC error"));

        try (MockedStatic<AuditLedgerContract> mocked = mockStatic(AuditLedgerContract.class)) {
            mocked.when(() -> AuditLedgerContract.load(anyString(), any(), any(), any()))
                    .thenReturn(contract);

            BlockchainWriterService fastRetryService =
                    new BlockchainWriterService(web3j, credentials, props, hashService, 0L);

            assertThatThrownBy(() -> fastRetryService.anchorEvent(event))
                    .isInstanceOf(BlockchainWriterService.BlockchainWriteException.class)
                    .hasMessageContaining("Failed to anchor event");
        }
    }

    @Test
    void anchorEvent_failsFastOnMissingEventId() {
        // eventId is null; occurredAt and eventType are valid → should fail on eventId check
        UserLoggedInEvent event = UserLoggedInEvent.builder().build();
        event.setEventType(EventType.USER_LOGGED_IN);
        event.setOccurredAt(Instant.now());

        assertThatThrownBy(() -> service.anchorEvent(event))
                .isInstanceOf(BlockchainWriterService.NonRecoverableEventException.class)
                .hasMessageContaining("eventId");
    }

    @Test
    void anchorEvent_failsFastOnMissingEventType() {
        // eventId and occurredAt are valid; eventType is null → should fail on eventType check
        UserLoggedInEvent event = UserLoggedInEvent.builder().build();
        event.setEventId("evt-1");
        event.setOccurredAt(Instant.now());
        // eventType intentionally left null

        assertThatThrownBy(() -> service.anchorEvent(event))
                .isInstanceOf(BlockchainWriterService.NonRecoverableEventException.class)
                .hasMessageContaining("eventType");
    }
}


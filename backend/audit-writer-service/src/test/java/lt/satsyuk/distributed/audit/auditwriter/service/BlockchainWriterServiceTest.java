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
import java.util.Optional;

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
    void setUp() throws Exception {
        props = new Web3jProperties();
        props.setClientAddress("http://localhost:8545");
        props.setContractAddress("0xdeadbeefdeadbeefdeadbeefdeadbeefdeadbeef");
        props.setPrivateKey("0x0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef");

        hashService = new HashCalculationService(new JacksonConfig().objectMapper());

        lenient().when(credentials.getAddress()).thenReturn("0xsender");
        lenient().when(contract.owner()).thenReturn("0xsender");

        service = new BlockchainWriterService(web3j, Optional.of(credentials), props, hashService, 0L);
    }

    @Test
    void anchorEvent_failsWhenContractAddressBlank() {
        props.setContractAddress("");
        UserLoggedInEvent event = UserLoggedInEvent.of("u1", null, null);

        assertThatThrownBy(() -> service.anchorEvent(event))
                // BlockchainNotConfiguredException extends BlockchainWriteException
                .isInstanceOf(BlockchainWriterService.BlockchainNotConfiguredException.class)
                .hasMessageContaining("not configured");
    }

    @Test
    void anchorEvent_failsWhenCredentialsAbsent() {
        props.setPrivateKey("");
        BlockchainWriterService noCredService =
                new BlockchainWriterService(web3j, Optional.empty(), props, hashService, 0L);
        UserLoggedInEvent event = UserLoggedInEvent.of("u1", null, null);

        assertThatThrownBy(() -> noCredService.anchorEvent(event))
                // BlockchainNotConfiguredException extends BlockchainWriteException
                .isInstanceOf(BlockchainWriterService.BlockchainNotConfiguredException.class)
                .hasMessageContaining("not configured");
    }

    @Test
    void anchorEvent_failsWhenPrivateKeyMalformed() {
        props.setPrivateKey("0x1234");
        BlockchainWriterService noCredService =
                new BlockchainWriterService(web3j, Optional.empty(), props, hashService, 0L);
        UserLoggedInEvent event = UserLoggedInEvent.of("u1", null, null);

        assertThatThrownBy(() -> noCredService.anchorEvent(event))
                .isInstanceOf(BlockchainWriterService.BlockchainNotConfiguredException.class)
                .hasMessageContaining("private-key is malformed");
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
    void anchorEvent_treatsExistingHashAsSuccessEvenWhenSignerIsNotOwner() throws Exception {
        UserLoggedInEvent event = UserLoggedInEvent.of("u1", null, null);

        when(contract.isHashExists(any())).thenReturn(true);

        try (MockedStatic<AuditLedgerContract> mocked = mockStatic(AuditLedgerContract.class)) {
            mocked.when(() -> AuditLedgerContract.load(anyString(), any(), any(), any()))
                    .thenReturn(contract);

            assertThatCode(() -> service.anchorEvent(event)).doesNotThrowAnyException();
        }

        verify(contract, never()).owner();
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
    void anchorEvent_detectsDuplicateViaPostFailureIsHashExistsCheck() throws Exception {
        // Simulates the race-condition path where the DuplicateHash error is NOT surfaced
        // as literal text (custom Solidity error → ABI-encoded revert data only).
        // The post-failure isHashExists re-check must detect the duplicate deterministically.
        UserLoggedInEvent event = UserLoggedInEvent.of("u1", null, null);

        // Pre-flight check: false (hash not there yet)
        // appendAuditRecord: throws a generic exception (no "DuplicateHash" text)
        // Post-failure isHashExists: true (concurrent writer committed the same hash)
        when(contract.isHashExists(any()))
                .thenReturn(false)   // pre-flight
                .thenReturn(true);   // post-failure re-check
        when(contract.appendAuditRecord(any(), any(BigInteger.class), anyString(), anyString()))
                .thenThrow(new RuntimeException("execution reverted"));

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
                    new BlockchainWriterService(web3j, Optional.of(credentials), props, hashService, 0L);

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

    @Test
    void anchorEvent_rejectsFutureTimestampBeyondDefaultTolerance() {
        // Use a comfortable margin beyond the default 300s tolerance to avoid timing flakiness.
        UserLoggedInEvent event = UserLoggedInEvent.of("u1", null, null);
        event.setOccurredAt(Instant.now().plusSeconds(360));

        assertThatThrownBy(() -> service.anchorEvent(event))
                .isInstanceOf(BlockchainWriterService.NonRecoverableEventException.class)
                .hasMessageContaining("in the future");
    }

    @Test
    void anchorEvent_acceptsFutureTimestampWithinDefaultTolerance() throws Exception {
        // Default tolerance is 300 seconds; use 299 seconds in future
        UserLoggedInEvent event = UserLoggedInEvent.of("u1", null, null);
        event.setOccurredAt(Instant.now().plusSeconds(299));

        TransactionReceipt receipt = new TransactionReceipt();
        receipt.setTransactionHash("0xabc");
        receipt.setBlockNumber("0x1");

        try (MockedStatic<AuditLedgerContract> mocked = mockStatic(AuditLedgerContract.class)) {
            mocked.when(() -> AuditLedgerContract.load(anyString(), any(), any(), any()))
                    .thenReturn(contract);

            when(contract.isHashExists(any(byte[].class))).thenReturn(false);
            when(contract.appendAuditRecord(any(byte[].class), any(BigInteger.class), anyString(), anyString()))
                    .thenReturn(receipt);

            assertThatCode(() -> service.anchorEvent(event)).doesNotThrowAnyException();
        }
    }

    @Test
    void anchorEvent_rejectsFutureTimestampBeyondCustomTolerance() {
        // Use a comfortable margin beyond the custom tolerance to avoid timing flakiness.
        props.setFutureTimestampToleranceSeconds(60);
        UserLoggedInEvent event = UserLoggedInEvent.of("u1", null, null);
        event.setOccurredAt(Instant.now().plusSeconds(120));

        assertThatThrownBy(() -> service.anchorEvent(event))
                .isInstanceOf(BlockchainWriterService.NonRecoverableEventException.class)
                .hasMessageContaining("60s in the future");
    }

    @Test
    void anchorEvent_failsWhenFutureTimestampToleranceNegative() {
        props.setFutureTimestampToleranceSeconds(-1);
        UserLoggedInEvent event = UserLoggedInEvent.of("u1", null, null);

        assertThatThrownBy(() -> service.anchorEvent(event))
                .isInstanceOf(BlockchainWriterService.BlockchainNotConfiguredException.class)
                .hasMessageContaining("future-timestamp-tolerance-seconds");
    }

    @Test
    void anchorEvent_rejectsPreEpochTimestampAsNonRecoverable() {
        UserLoggedInEvent event = UserLoggedInEvent.of("u1", null, null);
        event.setOccurredAt(Instant.ofEpochSecond(-1));

        assertThatThrownBy(() -> service.anchorEvent(event))
                .isInstanceOf(BlockchainWriterService.NonRecoverableEventException.class)
                .hasMessageContaining("before Unix epoch");
    }

    @Test
    void anchorEvent_failsWhenContractAddressIsZeroAddress() {
        props.setContractAddress("0x0000000000000000000000000000000000000000");
        UserLoggedInEvent event = UserLoggedInEvent.of("u1", null, null);

        assertThatThrownBy(() -> service.anchorEvent(event))
                .isInstanceOf(BlockchainWriterService.BlockchainNotConfiguredException.class)
                .hasMessageContaining("zero-address");
    }

    @Test
    void anchorEvent_failsWhenPrivateKeyHasUppercase0XPrefix() {
        props.setPrivateKey("0X0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef");
        BlockchainWriterService noCredService =
                new BlockchainWriterService(web3j, Optional.empty(), props, hashService, 0L);
        UserLoggedInEvent event = UserLoggedInEvent.of("u1", null, null);

        assertThatThrownBy(() -> noCredService.anchorEvent(event))
                .isInstanceOf(BlockchainWriterService.BlockchainNotConfiguredException.class)
                .hasMessageContaining("private-key is malformed");
    }

    @Test
    void anchorEvent_treatsFailedReceiptStatusAsWriteFailure() throws Exception {
        UserLoggedInEvent event = UserLoggedInEvent.of("u1", null, null);

        TransactionReceipt failedReceipt = new TransactionReceipt();
        failedReceipt.setStatus("0x0");
        failedReceipt.setTransactionHash("0xdead");

        when(contract.isHashExists(any())).thenReturn(false);
        when(contract.appendAuditRecord(any(), any(BigInteger.class), anyString(), anyString()))
                .thenReturn(failedReceipt);

        try (MockedStatic<AuditLedgerContract> mocked = mockStatic(AuditLedgerContract.class)) {
            mocked.when(() -> AuditLedgerContract.load(anyString(), any(), any(), any()))
                    .thenReturn(contract);

            assertThatThrownBy(() -> service.anchorEvent(event))
                    .isInstanceOf(BlockchainWriterService.BlockchainWriteException.class)
                    .hasMessageContaining("Failed to anchor event");
        }

        verify(contract, times(BlockchainWriterService.MAX_RETRIES))
                .appendAuditRecord(any(), any(BigInteger.class), anyString(), anyString());
    }

    @Test
    void anchorEvent_mapsUnauthorizedFailedReceiptToNotConfigured() throws Exception {
        UserLoggedInEvent event = UserLoggedInEvent.of("u1", null, null);

        TransactionReceipt failedReceipt = new TransactionReceipt();
        failedReceipt.setStatus("0x0");
        failedReceipt.setTransactionHash("0xbad");
        failedReceipt.setRevertReason("Unauthorized()");

        when(contract.isHashExists(any())).thenReturn(false);
        when(contract.appendAuditRecord(any(), any(BigInteger.class), anyString(), anyString()))
                .thenReturn(failedReceipt);

        try (MockedStatic<AuditLedgerContract> mocked = mockStatic(AuditLedgerContract.class)) {
            mocked.when(() -> AuditLedgerContract.load(anyString(), any(), any(), any()))
                    .thenReturn(contract);

            assertThatThrownBy(() -> service.anchorEvent(event))
                    .isInstanceOf(BlockchainWriterService.BlockchainNotConfiguredException.class)
                    .hasMessageContaining("does not own the contract");
        }
    }

    @Test
    void anchorEvent_failsFastWhenSignerIsNotContractOwner() throws Exception {
        UserLoggedInEvent event = UserLoggedInEvent.of("u1", null, null);

        when(contract.owner()).thenReturn("0x0000000000000000000000000000000000000001");

        try (MockedStatic<AuditLedgerContract> mocked = mockStatic(AuditLedgerContract.class)) {
            mocked.when(() -> AuditLedgerContract.load(anyString(), any(), any(), any()))
                    .thenReturn(contract);

            assertThatThrownBy(() -> service.anchorEvent(event))
                    .isInstanceOf(BlockchainWriterService.BlockchainNotConfiguredException.class)
                    .hasMessageContaining("does not own AuditLedger contract");
        }

        verify(contract, never()).appendAuditRecord(any(), any(), any(), any());
    }

    @Test
    void anchorEvent_failsWhenContractOwnerCannotBeResolved() throws Exception {
        UserLoggedInEvent event = UserLoggedInEvent.of("u1", null, null);

        when(contract.owner()).thenReturn(null);

        try (MockedStatic<AuditLedgerContract> mocked = mockStatic(AuditLedgerContract.class)) {
            mocked.when(() -> AuditLedgerContract.load(anyString(), any(), any(), any()))
                    .thenReturn(contract);

            assertThatThrownBy(() -> service.anchorEvent(event))
                    .isInstanceOf(BlockchainWriterService.BlockchainNotConfiguredException.class)
                    .hasMessageContaining("empty owner response");
        }

        verify(contract, never()).appendAuditRecord(any(), any(), any(), any());
    }
}


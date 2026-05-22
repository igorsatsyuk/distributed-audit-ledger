package lt.satsyuk.distributed.audit.auditwriter.service;

import lt.satsyuk.distributed.audit.auditwriter.blockchain.AuditLedgerContract;
import lt.satsyuk.distributed.audit.auditwriter.config.JacksonConfig;
import lt.satsyuk.distributed.audit.auditwriter.config.Web3jProperties;
import lt.satsyuk.distributed.audit.event.EventType;
import lt.satsyuk.distributed.audit.event.UserLoggedInEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.stubbing.Answer;
import org.web3j.crypto.Credentials;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.methods.response.TransactionReceipt;

import java.math.BigInteger;
import java.time.Instant;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static lt.satsyuk.distributed.audit.auditwriter.testutil.TestWaitUtils.pauseWithoutThreadSleep;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.junit.jupiter.params.provider.Arguments.arguments;
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
    private static final String SECP256K1_ORDER_HEX = "FFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFEBAAEDCE6AF48A03BBFD25E8CD0364141";
    private static final String IN_FLIGHT_WRITE_CLASS_NAME_SUFFIX = "$InFlightWrite";

    @BeforeEach
    void setUp() {
        props = new Web3jProperties();
        props.setClientAddress("http://localhost:8545");
        props.setContractAddress("0xdeadbeefdeadbeefdeadbeefdeadbeefdeadbeef");
        props.setPrivateKey("0x0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef");
        props.setReceiptWaitTimeoutSeconds(1);

        hashService = new HashCalculationService(new JacksonConfig().objectMapper());

        lenient().when(credentials.getAddress()).thenReturn("0xsender");
        lenient().when(contract.owner()).thenReturn("0xsender");

        service = new BlockchainWriterService(web3j, credentials, props, hashService, 0L);
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

    @ParameterizedTest(name = "privateKey={0} -> message contains: {1}")
    @MethodSource("invalidPrivateKeyCases")
    void anchorEvent_failsWhenCredentialsAbsentOrPrivateKeyMalformed(String privateKey,
                                                                      String expectedMessagePart) {
        props.setPrivateKey(privateKey);
        BlockchainWriterService noCredService =
                new BlockchainWriterService(web3j, null, props, hashService, 0L);
        UserLoggedInEvent event = UserLoggedInEvent.of("u1", null, null);

        assertThatThrownBy(() -> noCredService.anchorEvent(event))
                .isInstanceOf(BlockchainWriterService.BlockchainNotConfiguredException.class)
                .hasMessageContaining(expectedMessagePart);
    }

    private static Stream<Arguments> invalidPrivateKeyCases() {
        return Stream.of(
                arguments("", "not configured"),
                arguments("0x1234", "private-key is malformed"),
                arguments("0x0000000000000000000000000000000000000000000000000000000000000000", "private-key is malformed"),
                arguments("0x" + SECP256K1_ORDER_HEX, "private-key is malformed"),
                arguments("0X0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef", "private-key is malformed")
        );
    }

    @Test
    void anchorEvent_successOnFirstAttempt() {
        UserLoggedInEvent event = UserLoggedInEvent.of("u1", "1.2.3.4", null);
        TransactionReceipt receipt = new TransactionReceipt();
        receipt.setStatus("0x1");
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
    void anchorEvent_treatsExistingHashAsSuccess() {
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
    void anchorEvent_skipsOwnerLookupWhenHashAlreadyExists() {
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
    void anchorEvent_treatsDuplicateHashContractRevertAsSuccess() {
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
    void anchorEvent_detectsDuplicateViaPostFailureIsHashExistsCheck() {
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
    void anchorEvent_throwsBlockchainWriteExceptionAfterAllRetriesExhausted() {
        UserLoggedInEvent event = UserLoggedInEvent.of("u1", null, null);

        when(contract.isHashExists(any())).thenReturn(false);
        when(contract.appendAuditRecord(any(), any(BigInteger.class), anyString(), anyString()))
                .thenThrow(new RuntimeException("RPC error"));

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
    void anchorEvent_respectsCustomRetryCount_retriesInAdditionToInitialAttempt() {
        props.setBlockchainWriteRetries(1);
        UserLoggedInEvent event = UserLoggedInEvent.of("u1", null, null);

        when(contract.isHashExists(any())).thenReturn(false);
        when(contract.appendAuditRecord(any(), any(BigInteger.class), anyString(), anyString()))
                .thenThrow(new RuntimeException("RPC error"));

        try (MockedStatic<AuditLedgerContract> mocked = mockStatic(AuditLedgerContract.class)) {
            mocked.when(() -> AuditLedgerContract.load(anyString(), any(), any(), any()))
                    .thenReturn(contract);

            assertThatThrownBy(() -> service.anchorEvent(event))
                    .isInstanceOf(BlockchainWriterService.BlockchainWriteException.class)
                    .hasMessageContaining("after 2 attempts");
        }

        verify(contract, times(2))
                .appendAuditRecord(any(), any(BigInteger.class), anyString(), anyString());
    }

    @Test
    void anchorEvent_failsFastWhenRetryCountIsNegative() {
        props.setBlockchainWriteRetries(-1);
        UserLoggedInEvent event = UserLoggedInEvent.of("u1", null, null);

        assertThatThrownBy(() -> service.anchorEvent(event))
                .isInstanceOf(BlockchainWriterService.BlockchainNotConfiguredException.class)
                .hasMessageContaining("blockchain-write-retries must be >= 0");
    }

    @Test
    void anchorEvent_failsFastWhenRetryCountExceedsUpperBound() {
        props.setBlockchainWriteRetries(11);
        UserLoggedInEvent event = UserLoggedInEvent.of("u1", null, null);

        assertThatThrownBy(() -> service.anchorEvent(event))
                .isInstanceOf(BlockchainWriterService.BlockchainNotConfiguredException.class)
                .hasMessageContaining("blockchain-write-retries must be <= 10");
    }

    @Test
    void anchorEvent_failsFastWhenReceiptWaitTimeoutIsNotPositive() {
        props.setReceiptWaitTimeoutSeconds(0);
        UserLoggedInEvent event = UserLoggedInEvent.of("u1", null, null);

        assertThatThrownBy(() -> service.anchorEvent(event))
                .isInstanceOf(BlockchainWriterService.BlockchainNotConfiguredException.class)
                .hasMessageContaining("receipt-wait-timeout-seconds must be > 0");
    }

    @Test
    void anchorEvent_failsFastWhenClientAddressMissing() {
        props.setClientAddress(" ");
        UserLoggedInEvent event = UserLoggedInEvent.of("u1", null, null);

        assertThatThrownBy(() -> service.anchorEvent(event))
                .isInstanceOf(BlockchainWriterService.BlockchainNotConfiguredException.class)
                .hasMessageContaining("web3j.client-address is missing");
    }

    @Test
    void anchorEvent_failsFastWhenClientAddressMalformed() {
        props.setClientAddress("localhost:8545");
        UserLoggedInEvent event = UserLoggedInEvent.of("u1", null, null);

        assertThatThrownBy(() -> service.anchorEvent(event))
                .isInstanceOf(BlockchainWriterService.BlockchainNotConfiguredException.class)
                .hasMessageContaining("malformed web3j.client-address");
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
        event.setEventId("00000000-0000-0000-0000-000000000001");
        event.setOccurredAt(Instant.now());
        // eventType intentionally left null

        assertThatThrownBy(() -> service.anchorEvent(event))
                .isInstanceOf(BlockchainWriterService.NonRecoverableEventException.class)
                .hasMessageContaining("eventType");
    }

    @Test
    void anchorEvent_allowsNullOccurredAtUsingFallbackTimestamp() {
        UserLoggedInEvent event = UserLoggedInEvent.of("u1", null, null);
        event.setOccurredAt(null);

        TransactionReceipt receipt = new TransactionReceipt();
        receipt.setStatus("0x1");
        receipt.setTransactionHash("0xabc");
        receipt.setBlockNumber("0x1");

        when(contract.isHashExists(any(byte[].class))).thenReturn(false);
        when(contract.appendAuditRecord(any(byte[].class), any(BigInteger.class), anyString(), anyString()))
                .thenReturn(receipt);

        try (MockedStatic<AuditLedgerContract> mocked = mockStatic(AuditLedgerContract.class)) {
            mocked.when(() -> AuditLedgerContract.load(anyString(), any(), any(), any()))
                    .thenReturn(contract);

            assertThatCode(() -> service.anchorEvent(event)).doesNotThrowAnyException();
        }

        verify(contract).appendAuditRecord(any(byte[].class), any(BigInteger.class), anyString(), anyString());
    }

    @Test
    void anchorEvent_allowsNullUserIdToStayAlignedWithEventStoreFallbacks() {
        UserLoggedInEvent event = UserLoggedInEvent.of("u1", null, null);
        event.setUserId(null);

        TransactionReceipt receipt = new TransactionReceipt();
        receipt.setStatus("0x1");
        receipt.setTransactionHash("0xabc");
        receipt.setBlockNumber("0x1");

        when(contract.isHashExists(any(byte[].class))).thenReturn(false);
        when(contract.appendAuditRecord(any(byte[].class), any(BigInteger.class), anyString(), anyString()))
                .thenReturn(receipt);

        try (MockedStatic<AuditLedgerContract> mocked = mockStatic(AuditLedgerContract.class)) {
            mocked.when(() -> AuditLedgerContract.load(anyString(), any(), any(), any()))
                    .thenReturn(contract);

            assertThatCode(() -> service.anchorEvent(event)).doesNotThrowAnyException();
        }
    }

    @Test
    void anchorEvent_allowsFutureTimestampBeyondDefaultToleranceToStayAlignedWithEventStore() {
        // Even when beyond tolerance, audit-writer should not DLT the record only due to clock skew.
        UserLoggedInEvent event = UserLoggedInEvent.of("u1", null, null);
        event.setOccurredAt(Instant.now().plusSeconds(360));

        TransactionReceipt receipt = new TransactionReceipt();
        receipt.setStatus("0x1");
        receipt.setTransactionHash("0xabc");
        receipt.setBlockNumber("0x1");

        when(contract.isHashExists(any(byte[].class))).thenReturn(false);
        when(contract.appendAuditRecord(any(byte[].class), any(BigInteger.class), anyString(), anyString()))
                .thenReturn(receipt);

        try (MockedStatic<AuditLedgerContract> mocked = mockStatic(AuditLedgerContract.class)) {
            mocked.when(() -> AuditLedgerContract.load(anyString(), any(), any(), any()))
                    .thenReturn(contract);

            assertThatCode(() -> service.anchorEvent(event)).doesNotThrowAnyException();
        }
    }

    @Test
    void anchorEvent_allowsNonUuidEventIdToStayAlignedWithEventStore() {
        UserLoggedInEvent event = UserLoggedInEvent.of("u1", null, null);
        event.setEventId("xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx");

        TransactionReceipt receipt = new TransactionReceipt();
        receipt.setStatus("0x1");
        receipt.setTransactionHash("0xabc");
        receipt.setBlockNumber("0x1");

        when(contract.isHashExists(any(byte[].class))).thenReturn(false);
        when(contract.appendAuditRecord(any(byte[].class), any(BigInteger.class), anyString(), anyString()))
                .thenReturn(receipt);

        try (MockedStatic<AuditLedgerContract> mocked = mockStatic(AuditLedgerContract.class)) {
            mocked.when(() -> AuditLedgerContract.load(anyString(), any(), any(), any()))
                    .thenReturn(contract);

            assertThatCode(() -> service.anchorEvent(event)).doesNotThrowAnyException();
        }
    }

    @Test
    void anchorEvent_rejectsEventIdWithWrongLength() {
        UserLoggedInEvent event = UserLoggedInEvent.of("u1", null, null);
        event.setEventId("x".repeat(37));

        assertThatThrownBy(() -> service.anchorEvent(event))
                .isInstanceOf(BlockchainWriterService.NonRecoverableEventException.class)
                .hasMessageContaining("eventId exceeds 36 chars");
    }

    @Test
    void anchorEvent_acceptsFutureTimestampWithinDefaultTolerance() {
        // Default tolerance is 300 seconds; use 299 seconds in future
        UserLoggedInEvent event = UserLoggedInEvent.of("u1", null, null);
        event.setOccurredAt(Instant.now().plusSeconds(299));

        TransactionReceipt receipt = new TransactionReceipt();
        receipt.setStatus("0x1");
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
    void anchorEvent_allowsFutureTimestampBeyondCustomToleranceToStayAlignedWithEventStore() {
        props.setFutureTimestampToleranceSeconds(60);
        UserLoggedInEvent event = UserLoggedInEvent.of("u1", null, null);
        event.setOccurredAt(Instant.now().plusSeconds(120));

        TransactionReceipt receipt = new TransactionReceipt();
        receipt.setStatus("0x1");
        receipt.setTransactionHash("0xabc");
        receipt.setBlockNumber("0x1");

        when(contract.isHashExists(any(byte[].class))).thenReturn(false);
        when(contract.appendAuditRecord(any(byte[].class), any(BigInteger.class), anyString(), anyString()))
                .thenReturn(receipt);

        try (MockedStatic<AuditLedgerContract> mocked = mockStatic(AuditLedgerContract.class)) {
            mocked.when(() -> AuditLedgerContract.load(anyString(), any(), any(), any()))
                    .thenReturn(contract);

            assertThatCode(() -> service.anchorEvent(event)).doesNotThrowAnyException();
        }
    }

    @Test
    void anchorEvent_rejectsUserIdTooLongForAggregateId() {
        String tooLongUserId = "u".repeat(124);
        UserLoggedInEvent event = UserLoggedInEvent.of(tooLongUserId, null, null);

        assertThatThrownBy(() -> service.anchorEvent(event))
                .isInstanceOf(BlockchainWriterService.NonRecoverableEventException.class)
                .hasMessageContaining("aggregate_id length exceeds");
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
    void anchorEvent_normalizesPreEpochTimestampForOnChainWrite() {
        UserLoggedInEvent event = UserLoggedInEvent.of("u1", null, null);
        event.setOccurredAt(Instant.ofEpochSecond(-1));

        TransactionReceipt receipt = new TransactionReceipt();
        receipt.setStatus("0x1");
        receipt.setTransactionHash("0xabc");
        receipt.setBlockNumber("0x1");

        when(contract.isHashExists(any(byte[].class))).thenReturn(false);
        when(contract.appendAuditRecord(any(byte[].class), any(BigInteger.class), anyString(), anyString()))
                .thenReturn(receipt);

        try (MockedStatic<AuditLedgerContract> mocked = mockStatic(AuditLedgerContract.class)) {
            mocked.when(() -> AuditLedgerContract.load(anyString(), any(), any(), any()))
                    .thenReturn(contract);

            assertThatCode(() -> service.anchorEvent(event)).doesNotThrowAnyException();
        }

        verify(contract).appendAuditRecord(any(byte[].class), eq(BigInteger.ZERO), anyString(), anyString());
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
    void anchorEvent_treatsMissingReceiptStatusAsWriteFailure() {
        UserLoggedInEvent event = UserLoggedInEvent.of("u1", null, null);

        TransactionReceipt receiptWithoutStatus = new TransactionReceipt();
        receiptWithoutStatus.setTransactionHash("0xdead");

        when(contract.isHashExists(any())).thenReturn(false);
        when(contract.appendAuditRecord(any(), any(BigInteger.class), anyString(), anyString()))
                .thenReturn(receiptWithoutStatus);

        try (MockedStatic<AuditLedgerContract> mocked = mockStatic(AuditLedgerContract.class)) {
            mocked.when(() -> AuditLedgerContract.load(anyString(), any(), any(), any()))
                    .thenReturn(contract);

            assertThatThrownBy(() -> service.anchorEvent(event))
                    .isInstanceOf(BlockchainWriterService.BlockchainWriteException.class)
                    .hasMessageContaining("Failed to anchor event");
        }
    }

    @Test
    void anchorEvent_treatsFailedReceiptStatusAsWriteFailure() {
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

        verify(contract, times(props.getBlockchainWriteRetries() + 1))
                .appendAuditRecord(any(), any(BigInteger.class), anyString(), anyString());
    }

    @Test
    void anchorEvent_doesNotRetryInsideServiceWhenReceiptWaitTimesOut() {
        UserLoggedInEvent event = UserLoggedInEvent.of("u1", null, null);
        props.setReceiptWaitTimeoutSeconds(1);
        props.setBlockchainWriteRetries(3);

        when(contract.isHashExists(any())).thenReturn(false);
        when(contract.appendAuditRecord(any(), any(BigInteger.class), anyString(), anyString()))
                .thenAnswer(delayedReceiptAnswer(2_000L, successfulReceipt("0xlate")));

        try (MockedStatic<AuditLedgerContract> mocked = mockStatic(AuditLedgerContract.class)) {
            mocked.when(() -> AuditLedgerContract.load(anyString(), any(), any(), any()))
                    .thenReturn(contract);

            assertThatThrownBy(() -> service.anchorEvent(event))
                    .isInstanceOf(BlockchainWriterService.ReceiptTimeoutException.class)
                    .hasMessageContaining("Timed out waiting 1s");
        }

        verify(contract, times(1)).appendAuditRecord(any(), any(BigInteger.class), anyString(), anyString());
    }

    @Test
    void anchorEvent_treatsReceiptExecutorSaturationAsReceiptTimeout() {
        UserLoggedInEvent event = UserLoggedInEvent.of("u1", null, null);
        ExecutorService rejectingExecutor = mock(ExecutorService.class);

        when(contract.isHashExists(any())).thenReturn(false);
        when(rejectingExecutor.submit(any(Callable.class)))
                .thenThrow(new RejectedExecutionException("executor queue is full"));

        BlockchainWriterService serviceWithRejectingExecutor =
                new BlockchainWriterService(web3j, credentials, props, hashService, 0L, rejectingExecutor);

        try (MockedStatic<AuditLedgerContract> mocked = mockStatic(AuditLedgerContract.class)) {
            mocked.when(() -> AuditLedgerContract.load(anyString(), any(), any(), any()))
                    .thenReturn(contract);

            assertThatThrownBy(() -> serviceWithRejectingExecutor.anchorEvent(event))
                    .isInstanceOf(BlockchainWriterService.ReceiptTimeoutException.class)
                    .hasMessageContaining("executor is saturated");
        }

        verify(contract, never()).appendAuditRecord(any(), any(), any(), any());
    }

    @Test
    void anchorEvent_mapsUnauthorizedFailedReceiptToNotConfigured() {
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
    void anchorEvent_failsFastWhenSignerIsNotContractOwner() {
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
    void anchorEvent_failsWhenContractOwnerCannotBeResolved() {
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

    @Test
    void anchorEvent_wrapsHashExistsProbeFailureAsNotConfigured() {
        UserLoggedInEvent event = UserLoggedInEvent.of("u1", null, null);

        when(contract.isHashExists(any())).thenThrow(new RuntimeException("no contract at address"));

        try (MockedStatic<AuditLedgerContract> mocked = mockStatic(AuditLedgerContract.class)) {
            mocked.when(() -> AuditLedgerContract.load(anyString(), any(), any(), any()))
                    .thenReturn(contract);

            assertThatThrownBy(() -> service.anchorEvent(event))
                    .isInstanceOf(BlockchainWriterService.BlockchainNotConfiguredException.class)
                    .hasMessageContaining("isHashExists probe failed");
        }

        verify(contract, never()).appendAuditRecord(any(), any(), any(), any());
    }

    @Test
    void anchorEvent_retriesWhenHashExistsProbeFailsTransiently() {
        UserLoggedInEvent event = UserLoggedInEvent.of("u1", null, null);
        props.setBlockchainWriteRetries(1);

        when(contract.isHashExists(any()))
                .thenThrow(new RuntimeException("connection reset by peer"))
                .thenReturn(false);

        TransactionReceipt receipt = new TransactionReceipt();
        receipt.setStatus("0x1");
        receipt.setTransactionHash("0xabc");
        receipt.setBlockNumber("0x1");
        when(contract.appendAuditRecord(any(), any(BigInteger.class), anyString(), anyString()))
                .thenReturn(receipt);

        try (MockedStatic<AuditLedgerContract> mocked = mockStatic(AuditLedgerContract.class)) {
            mocked.when(() -> AuditLedgerContract.load(anyString(), any(), any(), any()))
                    .thenReturn(contract);

            assertThatCode(() -> service.anchorEvent(event)).doesNotThrowAnyException();
        }

        verify(contract, times(2)).isHashExists(any());
        verify(contract).appendAuditRecord(any(), any(BigInteger.class), anyString(), anyString());
    }

    @Test
    void anchorEvent_wrapsOwnerProbeFailureAsNotConfigured() {
        UserLoggedInEvent event = UserLoggedInEvent.of("u1", null, null);

        when(contract.owner()).thenThrow(new RuntimeException("no contract owner"));
        when(contract.isHashExists(any())).thenReturn(false);

        try (MockedStatic<AuditLedgerContract> mocked = mockStatic(AuditLedgerContract.class)) {
            mocked.when(() -> AuditLedgerContract.load(anyString(), any(), any(), any()))
                    .thenReturn(contract);

            assertThatThrownBy(() -> service.anchorEvent(event))
                    .isInstanceOf(BlockchainWriterService.BlockchainNotConfiguredException.class)
                    .hasMessageContaining("Cannot resolve AuditLedger owner");
        }

        verify(contract, never()).appendAuditRecord(any(), any(), any(), any());
    }

    @Test
    void anchorEvent_retriesWhenOwnerProbeFailsTransiently() {
        UserLoggedInEvent event = UserLoggedInEvent.of("u1", null, null);
        props.setBlockchainWriteRetries(1);

        when(contract.isHashExists(any())).thenReturn(false);
        when(contract.owner())
                .thenThrow(new RuntimeException("rpc timeout"))
                .thenReturn("0xsender");

        TransactionReceipt receipt = new TransactionReceipt();
        receipt.setStatus("0x1");
        receipt.setTransactionHash("0xabc");
        receipt.setBlockNumber("0x1");
        when(contract.appendAuditRecord(any(), any(BigInteger.class), anyString(), anyString()))
                .thenReturn(receipt);

        try (MockedStatic<AuditLedgerContract> mocked = mockStatic(AuditLedgerContract.class)) {
            mocked.when(() -> AuditLedgerContract.load(anyString(), any(), any(), any()))
                    .thenReturn(contract);

            assertThatCode(() -> service.anchorEvent(event)).doesNotThrowAnyException();
        }

        verify(contract, times(2)).owner();
        verify(contract).appendAuditRecord(any(), any(BigInteger.class), anyString(), anyString());
    }

    @Test
    void anchorEvent_revalidatesOwnerOnEachWrite() {
        UserLoggedInEvent event1 = UserLoggedInEvent.of("u1", null, null);
        UserLoggedInEvent event2 = UserLoggedInEvent.of("u2", null, null);

        TransactionReceipt receipt = new TransactionReceipt();
        receipt.setStatus("0x1");
        receipt.setTransactionHash("0xabc");
        receipt.setBlockNumber("0x1");

        when(contract.isHashExists(any())).thenReturn(false);
        when(contract.appendAuditRecord(any(), any(BigInteger.class), anyString(), anyString()))
                .thenReturn(receipt);

        try (MockedStatic<AuditLedgerContract> mocked = mockStatic(AuditLedgerContract.class)) {
            mocked.when(() -> AuditLedgerContract.load(anyString(), any(), any(), any()))
                    .thenReturn(contract);

            assertThatCode(() -> service.anchorEvent(event1)).doesNotThrowAnyException();
            assertThatCode(() -> service.anchorEvent(event2)).doesNotThrowAnyException();
        }

        verify(contract, times(2)).owner();
        verify(contract, times(2)).appendAuditRecord(any(), any(BigInteger.class), anyString(), anyString());
    }

    @Test
    void anchorEvent_redeliveryDoesNotSubmitSecondTxWhileFirstIsInFlight() {
        UserLoggedInEvent event = UserLoggedInEvent.of("u1", null, null);
        props.setReceiptWaitTimeoutSeconds(1);

        when(contract.isHashExists(any())).thenReturn(false);
        when(contract.appendAuditRecord(any(), any(BigInteger.class), anyString(), anyString()))
                .thenAnswer(delayedReceiptAnswer(1_500L, successfulReceipt("0xpending")));

        try (MockedStatic<AuditLedgerContract> mocked = mockStatic(AuditLedgerContract.class)) {
            mocked.when(() -> AuditLedgerContract.load(anyString(), any(), any(), any()))
                    .thenReturn(contract);

            assertThatThrownBy(() -> service.anchorEvent(event))
                    .isInstanceOf(BlockchainWriterService.ReceiptTimeoutException.class)
                    .hasMessageContaining("Timed out waiting 1s");

            assertThatCode(() -> service.anchorEvent(event)).doesNotThrowAnyException();
        }

        verify(contract, times(1)).appendAuditRecord(any(), any(BigInteger.class), anyString(), anyString());
    }

    @Test
    void anchorEvent_redeliveryDropsStaleInFlightEntryAndRechecksChain() throws Exception {
        UserLoggedInEvent event = UserLoggedInEvent.of("u1", null, null);
        props.setReceiptWaitTimeoutSeconds(1);
        Future<TransactionReceipt> staleFuture = new Future<>() {
            private volatile boolean cancelled;

            @Override
            public boolean cancel(boolean mayInterruptIfRunning) {
                cancelled = true;
                return true;
            }

            @Override
            public boolean isCancelled() {
                return cancelled;
            }

            @Override
            public boolean isDone() {
                return false;
            }

            @Override
            public TransactionReceipt get() {
                return null;
            }

            @Override
            public TransactionReceipt get(long timeout, java.util.concurrent.TimeUnit unit)
                    throws java.util.concurrent.TimeoutException {
                throw new java.util.concurrent.TimeoutException("still pending");
            }
        };

        byte[] hash = hashService.computeHash(event);
        String hexHash = HashCalculationService.toHexString(hash);

        java.lang.reflect.Field field = BlockchainWriterService.class.getDeclaredField("inFlightWritesByHash");
        field.setAccessible(true);
        @SuppressWarnings("unchecked")
        java.util.concurrent.ConcurrentHashMap<String, Object> inFlightWritesByHash =
                (java.util.concurrent.ConcurrentHashMap<String, Object>) field.get(service);

        Class<?> inFlightWriteClass = findInFlightWriteClass();
        java.lang.reflect.Constructor<?> ctor = inFlightWriteClass.getDeclaredConstructor(Future.class, long.class);
        ctor.setAccessible(true);
        Object staleEntry = ctor.newInstance(staleFuture, System.nanoTime() - TimeUnit.SECONDS.toNanos(3));
        inFlightWritesByHash.put(hexHash, staleEntry);

        when(contract.isHashExists(any())).thenReturn(true);

        try (MockedStatic<AuditLedgerContract> mocked = mockStatic(AuditLedgerContract.class)) {
            mocked.when(() -> AuditLedgerContract.load(anyString(), any(), any(), any()))
                    .thenReturn(contract);

            assertThatCode(() -> service.anchorEvent(event)).doesNotThrowAnyException();
        }

        assertThat(staleFuture.isCancelled()).isFalse();
        assertThat(inFlightWritesByHash).doesNotContainKey(hexHash);
        verify(contract, times(1)).isHashExists(any());
        verify(contract, never()).appendAuditRecord(any(), any(BigInteger.class), anyString(), anyString());
    }

    @Test
    void anchorEvent_redeliveryClassifiesCompletedInFlightUnauthorizedAsNotConfigured() {
        UserLoggedInEvent event = UserLoggedInEvent.of("u1", null, null);
        props.setReceiptWaitTimeoutSeconds(1);

        TransactionReceipt failedReceipt = new TransactionReceipt();
        failedReceipt.setStatus("0x0");
        failedReceipt.setTransactionHash("0xunauthorized");
        failedReceipt.setRevertReason("Unauthorized()");

        when(contract.isHashExists(any())).thenReturn(false);
        when(contract.appendAuditRecord(any(), any(BigInteger.class), anyString(), anyString()))
                .thenAnswer(delayedReceiptAnswer(1_200L, failedReceipt));

        try (MockedStatic<AuditLedgerContract> mocked = mockStatic(AuditLedgerContract.class)) {
            mocked.when(() -> AuditLedgerContract.load(anyString(), any(), any(), any()))
                    .thenReturn(contract);

            assertThatThrownBy(() -> service.anchorEvent(event))
                    .isInstanceOf(BlockchainWriterService.ReceiptTimeoutException.class);

            pauseWithoutThreadSleep(400L);

            assertThatThrownBy(() -> service.anchorEvent(event))
                    .isInstanceOf(BlockchainWriterService.BlockchainNotConfiguredException.class)
                    .hasMessageContaining("does not own the contract");
        }

        verify(contract, times(1)).appendAuditRecord(any(), any(BigInteger.class), anyString(), anyString());
    }

    @Test
    void anchorEvent_staleInFlightRecheckFailurePreservesReceiptTimeoutSemantics() throws Exception {
        UserLoggedInEvent event = UserLoggedInEvent.of("u1", null, null);
        props.setReceiptWaitTimeoutSeconds(1);

        Future<TransactionReceipt> staleFuture = new Future<>() {
            @Override
            public boolean cancel(boolean mayInterruptIfRunning) {
                return false;
            }

            @Override
            public boolean isCancelled() {
                return false;
            }

            @Override
            public boolean isDone() {
                return false;
            }

            @Override
            public TransactionReceipt get() {
                return null;
            }

            @Override
            public TransactionReceipt get(long timeout, java.util.concurrent.TimeUnit unit) {
                return null;
            }
        };

        byte[] hash = hashService.computeHash(event);
        String hexHash = HashCalculationService.toHexString(hash);

        java.lang.reflect.Field field = BlockchainWriterService.class.getDeclaredField("inFlightWritesByHash");
        field.setAccessible(true);
        @SuppressWarnings("unchecked")
        java.util.concurrent.ConcurrentHashMap<String, Object> inFlightWritesByHash =
                (java.util.concurrent.ConcurrentHashMap<String, Object>) field.get(service);

        Class<?> inFlightWriteClass = findInFlightWriteClass();
        java.lang.reflect.Constructor<?> ctor = inFlightWriteClass.getDeclaredConstructor(Future.class, long.class);
        ctor.setAccessible(true);
        Object staleEntry = ctor.newInstance(staleFuture, System.nanoTime() - TimeUnit.SECONDS.toNanos(3));
        inFlightWritesByHash.put(hexHash, staleEntry);

        when(contract.isHashExists(any())).thenThrow(new RuntimeException("rpc timeout"));

        try (MockedStatic<AuditLedgerContract> mocked = mockStatic(AuditLedgerContract.class)) {
            mocked.when(() -> AuditLedgerContract.load(anyString(), any(), any(), any()))
                    .thenReturn(contract);

            assertThatThrownBy(() -> service.anchorEvent(event))
                    .isInstanceOf(BlockchainWriterService.ReceiptTimeoutException.class)
                    .hasMessageContaining("chain re-check failed");
        }

        verify(contract, times(1)).isHashExists(any());
        verify(contract, never()).appendAuditRecord(any(), any(BigInteger.class), anyString(), anyString());
    }

    @Test
    void anchorEvent_interruptedWhileWaitingForInFlightReceiptKeepsUnknownOutcomePath() throws Exception {
        UserLoggedInEvent event = UserLoggedInEvent.of("u1", null, null);
        props.setReceiptWaitTimeoutSeconds(1);

        Future<TransactionReceipt> interruptedFuture = new Future<>() {
            @Override
            public boolean cancel(boolean mayInterruptIfRunning) {
                return false;
            }

            @Override
            public boolean isCancelled() {
                return false;
            }

            @Override
            public boolean isDone() {
                return false;
            }

            @Override
            public TransactionReceipt get() {
                return null;
            }

            @Override
            public TransactionReceipt get(long timeout, java.util.concurrent.TimeUnit unit) throws InterruptedException {
                throw new InterruptedException("interrupted");
            }
        };

        byte[] hash = hashService.computeHash(event);
        String hexHash = HashCalculationService.toHexString(hash);

        java.lang.reflect.Field field = BlockchainWriterService.class.getDeclaredField("inFlightWritesByHash");
        field.setAccessible(true);
        @SuppressWarnings("unchecked")
        java.util.concurrent.ConcurrentHashMap<String, Object> inFlightWritesByHash =
                (java.util.concurrent.ConcurrentHashMap<String, Object>) field.get(service);

        Class<?> inFlightWriteClass = findInFlightWriteClass();
        java.lang.reflect.Constructor<?> ctor = inFlightWriteClass.getDeclaredConstructor(Future.class, long.class);
        ctor.setAccessible(true);
        Object inFlightEntry = ctor.newInstance(interruptedFuture, System.nanoTime());
        inFlightWritesByHash.put(hexHash, inFlightEntry);

        try (MockedStatic<AuditLedgerContract> mocked = mockStatic(AuditLedgerContract.class)) {
            mocked.when(() -> AuditLedgerContract.load(anyString(), any(), any(), any()))
                    .thenReturn(contract);

            assertThatThrownBy(() -> service.anchorEvent(event))
                    .isInstanceOf(BlockchainWriterService.ReceiptTimeoutException.class)
                    .hasMessageContaining("Interrupted while waiting");
        }

        // Reset interrupt flag for subsequent tests in the same JVM.
        assertThat(Thread.interrupted()).isTrue();
        verify(contract, never()).appendAuditRecord(any(), any(BigInteger.class), anyString(), anyString());
    }

    private static TransactionReceipt successfulReceipt(String transactionHash) {
        TransactionReceipt receipt = new TransactionReceipt();
        receipt.setStatus("0x1");
        receipt.setTransactionHash(transactionHash);
        receipt.setBlockNumber("0x1");
        return receipt;
    }

    private static Answer<TransactionReceipt> delayedReceiptAnswer(long delayMillis, TransactionReceipt receipt) {
        return invocation -> {
            pauseWithoutThreadSleep(delayMillis);
            return receipt;
        };
    }


    private static Class<?> findInFlightWriteClass() {
        return java.util.Arrays.stream(BlockchainWriterService.class.getDeclaredClasses())
                .filter(BlockchainWriterServiceTest::isInFlightWriteClass)
                .findFirst()
                .orElseThrow();
    }

    private static boolean isInFlightWriteClass(Class<?> candidate) {
        return candidate.getName().endsWith(IN_FLIGHT_WRITE_CLASS_NAME_SUFFIX);
    }
}


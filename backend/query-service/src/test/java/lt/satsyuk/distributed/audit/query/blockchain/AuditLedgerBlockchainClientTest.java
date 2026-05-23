package lt.satsyuk.distributed.audit.query.blockchain;

import lt.satsyuk.distributed.audit.query.api.BlockchainIntegrityException;
import lt.satsyuk.distributed.audit.query.api.AuditIntegrityCheckResponse;
import lt.satsyuk.distributed.audit.query.config.Web3jProperties;
import org.junit.jupiter.api.Test;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.Request;
import org.web3j.protocol.core.DefaultBlockParameter;
import org.web3j.protocol.core.DefaultBlockParameterName;
import org.web3j.protocol.core.methods.response.EthCall;
import org.web3j.protocol.core.methods.response.EthLog;
import org.web3j.protocol.core.methods.response.Log;
import org.web3j.protocol.core.Response;
import org.web3j.utils.Numeric;

import java.io.IOException;
import java.math.BigInteger;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.when;

class AuditLedgerBlockchainClientTest {

    private static final String VALID_HASH = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";

    @SuppressWarnings("unchecked")
    private static <S, T extends org.web3j.protocol.core.Response<?>> Request<S, T> mockRequest() {
        return mock(Request.class);
    }

    private static AuditIntegrityCheckResponse.BlockchainRecord inspectBlocking(AuditLedgerBlockchainClient client, String hash) {
        return client.inspectEventHash(hash).block();
    }

    @Test
    void resolveFromBlockParameterUsesEarliestForLocalGanache() {
        AuditLedgerBlockchainClient client = newClient("http://localhost:8545", 0L);

        DefaultBlockParameter result = client.resolveFromBlockParameter();

        assertEquals(DefaultBlockParameterName.EARLIEST, result);
    }

    @Test
    void resolveFromBlockParameterUsesEarliestForDockerHostGateway() {
        AuditLedgerBlockchainClient client = newClient("http://host.docker.internal:8545", 0L);

        DefaultBlockParameter result = client.resolveFromBlockParameter();

        assertEquals(DefaultBlockParameterName.EARLIEST, result);
    }


    @Test
    void resolveFromBlockParameterRejectsZeroBlockForNonLocalRpc() {
        AuditLedgerBlockchainClient client = newClient("https://example.com", 0L);

        BlockchainIntegrityException ex = assertThrows(BlockchainIntegrityException.class,
                client::resolveFromBlockParameter);
        assertEquals("web3j.contract-deployment-block must be configured for non-local RPC endpoints",
                ex.getMessage());
    }

    @Test
    void resolveFromBlockParameterRejectsNegativeBlock() {
        AuditLedgerBlockchainClient client = newClient("http://localhost:8545", -1L);

        BlockchainIntegrityException ex = assertThrows(BlockchainIntegrityException.class,
                client::resolveFromBlockParameter);
        assertEquals("web3j.contract-deployment-block must be >= 0", ex.getMessage());
    }

    @Test
    void resolveFromBlockParameterUsesConfiguredBlockNumber() {
        AuditLedgerBlockchainClient client = newClient("https://example.com", 42L);

        DefaultBlockParameter result = client.resolveFromBlockParameter();

        assertEquals("0x2a", result.getValue());
    }

    @Test
    void inspectEventHashRejectsMissingHash() {
        AuditLedgerBlockchainClient client = newClient("http://localhost:8545", 0L);

        BlockchainIntegrityException ex = assertThrows(BlockchainIntegrityException.class,
                () -> inspectBlocking(client, " "));

        assertEquals("event hash is missing", ex.getMessage());
        assertEquals(BlockchainIntegrityException.ErrorType.CONFIGURATION, ex.getErrorType());
    }

    @Test
    void inspectEventHashRejectsMalformedHash() {
        AuditLedgerBlockchainClient client = newClient("http://localhost:8545", 0L);

        BlockchainIntegrityException ex = assertThrows(BlockchainIntegrityException.class,
                () -> inspectBlocking(client, "0x1234"));

        assertEquals("event hash must be a 32-byte hex value", ex.getMessage());
        assertEquals(BlockchainIntegrityException.ErrorType.CONFIGURATION, ex.getErrorType());
    }

    @Test
    void inspectEventHashRejectsZeroAddressContract() {
        AuditLedgerBlockchainClient client = newClient(
                "http://localhost:8545",
                0L,
                "0x0000000000000000000000000000000000000000",
                mock(Web3j.class)
        );

        BlockchainIntegrityException ex = assertThrows(BlockchainIntegrityException.class,
                () -> inspectBlocking(client, VALID_HASH));

        assertEquals("web3j.contract-address cannot be zero-address (0x000...000)", ex.getMessage());
        assertEquals(BlockchainIntegrityException.ErrorType.CONFIGURATION, ex.getErrorType());
    }

    @Test
    void inspectEventHashReturnsNotFoundWhenContractSaysHashDoesNotExist() throws Exception {
        Web3j web3j = mock(Web3j.class);
        Request<?, EthCall> callRequest = mockRequest();
        doReturn(callRequest).when(web3j).ethCall(any(), eq(DefaultBlockParameterName.LATEST));

        EthCall ethCall = new EthCall();
        ethCall.setResult("0x" + "0".repeat(64));
        when(callRequest.send()).thenReturn(ethCall);

        AuditLedgerBlockchainClient client = newClient("http://localhost:8545", 0L,
                "0x1111111111111111111111111111111111111111", web3j);

        AuditIntegrityCheckResponse.BlockchainRecord blockchainRecord = inspectBlocking(client, VALID_HASH);

        assertNotNull(blockchainRecord);
        assertFalse(blockchainRecord.exists());
        assertNull(blockchainRecord.transactionHash());
        assertNull(blockchainRecord.blockNumber());
        assertNull(blockchainRecord.timestamp());
    }

    @Test
    void inspectEventHashReturnsDecodedTimestampWhenMatchingLogIsFound() throws Exception {
        Web3j web3j = mock(Web3j.class);
        Request<?, EthCall> callRequest = mockRequest();
        doReturn(callRequest).when(web3j).ethCall(any(), eq(DefaultBlockParameterName.LATEST));

        EthCall ethCall = new EthCall();
        ethCall.setResult("0x" + "0".repeat(63) + "1");
        when(callRequest.send()).thenReturn(ethCall);

        Request<?, EthLog> logsRequest = mockRequest();
        doReturn(logsRequest).when(web3j).ethGetLogs(any());

        Log log = new Log();
        log.setTransactionHash("0xfeed");
        log.setBlockNumber("0x2a");
        log.setData("0x" + Numeric.toHexStringNoPrefixZeroPadded(BigInteger.valueOf(1_704_067_200L), 64));

        @SuppressWarnings("unchecked")
        EthLog.LogResult<Log> logObject = mock(EthLog.LogResult.class);
        when(logObject.get()).thenReturn(log);

        EthLog ethLog = new EthLog();
        ethLog.setResult(Collections.singletonList(logObject));
        when(logsRequest.send()).thenReturn(ethLog);

        AuditLedgerBlockchainClient client = newClient("http://localhost:8545", 0L,
                "0x1111111111111111111111111111111111111111", web3j);

        AuditIntegrityCheckResponse.BlockchainRecord blockchainRecord = inspectBlocking(client, VALID_HASH);

        assertNotNull(blockchainRecord);
        assertTrue(blockchainRecord.exists());
        assertEquals("0xfeed", blockchainRecord.transactionHash());
        assertEquals(42L, blockchainRecord.blockNumber());
        assertEquals(1_704_067_200L, blockchainRecord.timestamp());
    }

    @Test
    void inspectEventHashFailsWhenEthCallResponseIsEmpty() throws Exception {
        Web3j web3j = mock(Web3j.class);
        Request<?, EthCall> callRequest = mockRequest();
        doReturn(callRequest).when(web3j).ethCall(any(), eq(DefaultBlockParameterName.LATEST));
        when(callRequest.send()).thenReturn(null);

        AuditLedgerBlockchainClient client = newClient("http://localhost:8545", 0L,
                "0x1111111111111111111111111111111111111111", web3j);

        BlockchainIntegrityException ex = assertThrows(BlockchainIntegrityException.class,
                () -> inspectBlocking(client, VALID_HASH));

        assertEquals("Blockchain call failed: empty eth_call response", ex.getMessage());
        assertEquals(BlockchainIntegrityException.ErrorType.RPC_FAILURE, ex.getErrorType());
    }

    @Test
    void inspectEventHashFailsWhenEthCallReturnsRpcError() throws Exception {
        Web3j web3j = mock(Web3j.class);
        Request<?, EthCall> callRequest = mockRequest();
        doReturn(callRequest).when(web3j).ethCall(any(), eq(DefaultBlockParameterName.LATEST));

        EthCall ethCall = new EthCall();
        ethCall.setError(new Response.Error(3, "execution reverted"));
        when(callRequest.send()).thenReturn(ethCall);

        AuditLedgerBlockchainClient client = newClient("http://localhost:8545", 0L,
                "0x1111111111111111111111111111111111111111", web3j);

        BlockchainIntegrityException ex = assertThrows(BlockchainIntegrityException.class,
                () -> inspectBlocking(client, VALID_HASH));

        assertEquals("Blockchain call failed: execution reverted", ex.getMessage());
        assertEquals(BlockchainIntegrityException.ErrorType.RPC_FAILURE, ex.getErrorType());
    }

    @Test
    void inspectEventHashFailsWhenEthCallReturnsUndecodablePayload() throws Exception {
        Web3j web3j = mock(Web3j.class);
        Request<?, EthCall> callRequest = mockRequest();
        doReturn(callRequest).when(web3j).ethCall(any(), eq(DefaultBlockParameterName.LATEST));

        EthCall ethCall = new EthCall();
        ethCall.setResult("0x");
        when(callRequest.send()).thenReturn(ethCall);

        AuditLedgerBlockchainClient client = newClient("http://localhost:8545", 0L,
                "0x1111111111111111111111111111111111111111", web3j);

        BlockchainIntegrityException ex = assertThrows(BlockchainIntegrityException.class,
                () -> inspectBlocking(client, VALID_HASH));

        assertEquals(
                "Blockchain contract mismatch: eth_call returned empty or undecodable value for isHashExists (check contract address / ABI)",
                ex.getMessage());
        assertEquals(BlockchainIntegrityException.ErrorType.CONFIGURATION, ex.getErrorType());
    }

    @Test
    void inspectEventHashFailsWhenEthGetLogsResponseIsEmpty() throws Exception {
        Web3j web3j = mock(Web3j.class);
        Request<?, EthCall> callRequest = mockRequest();
        doReturn(callRequest).when(web3j).ethCall(any(), eq(DefaultBlockParameterName.LATEST));

        EthCall ethCall = new EthCall();
        ethCall.setResult("0x" + "0".repeat(63) + "1");
        when(callRequest.send()).thenReturn(ethCall);

        Request<?, EthLog> logsRequest = mockRequest();
        doReturn(logsRequest).when(web3j).ethGetLogs(any());
        when(logsRequest.send()).thenReturn(null);

        AuditLedgerBlockchainClient client = newClient("http://localhost:8545", 0L,
                "0x1111111111111111111111111111111111111111", web3j);

        BlockchainIntegrityException ex = assertThrows(BlockchainIntegrityException.class,
                () -> inspectBlocking(client, VALID_HASH));

        assertEquals("Blockchain log lookup failed: empty eth_getLogs response", ex.getMessage());
        assertEquals(BlockchainIntegrityException.ErrorType.RPC_FAILURE, ex.getErrorType());
    }

    @Test
    void inspectEventHashReturnsExistsWithoutMetadataWhenLogsAreAbsent() throws Exception {
        Web3j web3j = mock(Web3j.class);
        Request<?, EthCall> callRequest = mockRequest();
        doReturn(callRequest).when(web3j).ethCall(any(), eq(DefaultBlockParameterName.LATEST));

        EthCall ethCall = new EthCall();
        ethCall.setResult("0x" + "0".repeat(63) + "1");
        when(callRequest.send()).thenReturn(ethCall);

        Request<?, EthLog> logsRequest = mockRequest();
        doReturn(logsRequest).when(web3j).ethGetLogs(any());
        EthLog ethLog = new EthLog();
        ethLog.setResult(Collections.emptyList());
        when(logsRequest.send()).thenReturn(ethLog);

        AuditLedgerBlockchainClient client = newClient("http://localhost:8545", 0L,
                "0x1111111111111111111111111111111111111111", web3j);

        AuditIntegrityCheckResponse.BlockchainRecord blockchainRecord = inspectBlocking(client, VALID_HASH);

        assertNotNull(blockchainRecord);
        assertTrue(blockchainRecord.exists());
        assertNull(blockchainRecord.transactionHash());
        assertNull(blockchainRecord.blockNumber());
        assertNull(blockchainRecord.timestamp());
    }

    @Test
    void inspectEventHashMapsUnexpectedErrorsToBlockchainIntegrityException() throws Exception {
        Web3j web3j = mock(Web3j.class);
        Request<?, EthCall> callRequest = mockRequest();
        doReturn(callRequest).when(web3j).ethCall(any(), eq(DefaultBlockParameterName.LATEST));
        when(callRequest.send()).thenThrow(new RuntimeException("boom"));

        AuditLedgerBlockchainClient client = newClient("http://localhost:8545", 0L,
                "0x1111111111111111111111111111111111111111", web3j);

        BlockchainIntegrityException ex = assertThrows(BlockchainIntegrityException.class,
                () -> inspectBlocking(client, VALID_HASH));

        assertEquals("Failed to read integrity data from blockchain", ex.getMessage());
        assertInstanceOf(RuntimeException.class, ex.getCause());
    }

    @Test
    void inspectEventHashWrapsIoExceptionsAsRpcFailure() throws Exception {
        Web3j web3j = mock(Web3j.class);
        Request<?, EthCall> callRequest = mockRequest();
        doReturn(callRequest).when(web3j).ethCall(any(), eq(DefaultBlockParameterName.LATEST));
        when(callRequest.send()).thenThrow(new IOException("rpc down"));

        AuditLedgerBlockchainClient client = newClient("http://localhost:8545", 0L,
                "0x1111111111111111111111111111111111111111", web3j);

        BlockchainIntegrityException ex = assertThrows(BlockchainIntegrityException.class,
                () -> inspectBlocking(client, VALID_HASH));

        assertEquals("Blockchain read failed", ex.getMessage());
        assertEquals(BlockchainIntegrityException.ErrorType.RPC_FAILURE, ex.getErrorType());
    }

    private AuditLedgerBlockchainClient newClient(String clientAddress, long deploymentBlock) {
        return newClient(
                clientAddress,
                deploymentBlock,
                "0x1111111111111111111111111111111111111111",
                mock(Web3j.class)
        );
    }

    private AuditLedgerBlockchainClient newClient(String clientAddress,
                                                  long deploymentBlock,
                                                  String contractAddress,
                                                  Web3j web3j) {
        Web3jProperties props = new Web3jProperties();
        props.setClientAddress(clientAddress);
        props.setContractDeploymentBlock(deploymentBlock);
        props.setContractAddress(contractAddress);
        return new AuditLedgerBlockchainClient(web3j, props);
    }

}


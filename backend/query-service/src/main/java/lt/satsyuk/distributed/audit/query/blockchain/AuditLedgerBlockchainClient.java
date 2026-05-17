package lt.satsyuk.distributed.audit.query.blockchain;

import lt.satsyuk.distributed.audit.query.api.AuditIntegrityCheckResponse;
import lt.satsyuk.distributed.audit.query.api.BlockchainIntegrityException;
import lt.satsyuk.distributed.audit.query.config.Web3jProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import org.web3j.abi.EventEncoder;
import org.web3j.abi.FunctionEncoder;
import org.web3j.abi.FunctionReturnDecoder;
import org.web3j.abi.datatypes.Address;
import org.web3j.abi.datatypes.Bool;
import org.web3j.abi.datatypes.Event;
import org.web3j.abi.datatypes.Function;
import org.web3j.abi.datatypes.Type;
import org.web3j.abi.datatypes.Utf8String;
import org.web3j.abi.datatypes.generated.Bytes32;
import org.web3j.abi.datatypes.generated.Uint256;
import org.web3j.abi.TypeReference;
import org.web3j.crypto.WalletUtils;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.DefaultBlockParameter;
import org.web3j.protocol.core.DefaultBlockParameterName;
import org.web3j.protocol.core.methods.request.Transaction;
import org.web3j.protocol.core.methods.response.EthCall;
import org.web3j.protocol.core.methods.response.EthLog;
import org.web3j.protocol.core.methods.response.Log;
import org.web3j.protocol.core.methods.request.EthFilter;
import org.web3j.utils.Numeric;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.net.URI;

@Service
public class AuditLedgerBlockchainClient {

    private static final Logger LOGGER = LoggerFactory.getLogger(AuditLedgerBlockchainClient.class);

    private static final Event RECORD_APPENDED_EVENT = new Event(
            "RecordAppended",
            Arrays.asList(
                    new TypeReference<Bytes32>(true) {},
                    new TypeReference<Uint256>() {},
                    new TypeReference<Utf8String>() {},
                    new TypeReference<Address>(true) {}
            )
    );

    private static final String RECORD_APPENDED_TOPIC = EventEncoder.encode(RECORD_APPENDED_EVENT);

    private final Web3j web3j;
    private final Web3jProperties props;

    public AuditLedgerBlockchainClient(Web3j web3j, Web3jProperties props) {
        this.web3j = web3j;
        this.props = props;
    }

    public Mono<AuditIntegrityCheckResponse.BlockchainRecord> inspectEventHash(String eventHash) {
        return Mono.fromCallable(() -> inspectEventHashBlocking(eventHash))
                .subscribeOn(Schedulers.boundedElastic())
                .onErrorMap(ex -> ex instanceof BlockchainIntegrityException
                        ? ex
                        : new BlockchainIntegrityException("Failed to read integrity data from blockchain", ex));
    }

    private AuditIntegrityCheckResponse.BlockchainRecord inspectEventHashBlocking(String eventHash) throws Exception {
        String contractAddress = requireText(props.getContractAddress(), "web3j.contract-address is missing");
        validateContractAddress(contractAddress);
        byte[] hashBytes = parseEventHash(eventHash);

        boolean exists = isHashExists(contractAddress, hashBytes);
        if (!exists) {
            return new AuditIntegrityCheckResponse.BlockchainRecord(false, null, null, null);
        }

        return locateBlockchainRecord(contractAddress, hashBytes)
                .orElseGet(() -> new AuditIntegrityCheckResponse.BlockchainRecord(true, null, null, null));
    }

    private boolean isHashExists(String contractAddress, byte[] hashBytes) throws Exception {
        Function function = new Function(
                "isHashExists",
                Collections.singletonList(new Bytes32(hashBytes)),
                Collections.singletonList(new TypeReference<Bool>() {})
        );

        String encodedInput = FunctionEncoder.encode(function);
        EthCall ethCall = web3j.ethCall(
                        Transaction.createEthCallTransaction(null, contractAddress, encodedInput),
                        DefaultBlockParameterName.LATEST)
                .send();

        if (ethCall == null) {
            throw new BlockchainIntegrityException("Blockchain call failed: empty eth_call response",
                    BlockchainIntegrityException.ErrorType.RPC_FAILURE);
        }
        if (ethCall.hasError()) {
            throw new BlockchainIntegrityException("Blockchain call failed: " + ethCall.getError().getMessage(),
                    BlockchainIntegrityException.ErrorType.RPC_FAILURE);
        }

        List<Type> decoded = FunctionReturnDecoder.decode(ethCall.getValue(), function.getOutputParameters());
        if (decoded == null || decoded.isEmpty()) {
            throw new BlockchainIntegrityException("Blockchain call failed: eth_call returned empty value for isHashExists",
                    BlockchainIntegrityException.ErrorType.RPC_FAILURE);
        }

        return Boolean.TRUE.equals(((Bool) decoded.get(0)).getValue());
    }

    private Optional<AuditIntegrityCheckResponse.BlockchainRecord> locateBlockchainRecord(String contractAddress,
                                                                                          byte[] hashBytes) throws Exception {
        EthFilter filter = new EthFilter(
                resolveFromBlockParameter(),
                DefaultBlockParameterName.LATEST,
                contractAddress
        );
        filter.addSingleTopic(RECORD_APPENDED_TOPIC);
        filter.addSingleTopic(Numeric.prependHexPrefix(Numeric.toHexStringNoPrefixZeroPadded(new BigInteger(1, hashBytes), 64)));

        EthLog ethLog = web3j.ethGetLogs(filter).send();
        if (ethLog == null) {
            throw new BlockchainIntegrityException("Blockchain log lookup failed: empty eth_getLogs response",
                    BlockchainIntegrityException.ErrorType.RPC_FAILURE);
        }
        if (ethLog.hasError()) {
            throw new BlockchainIntegrityException("Blockchain log lookup failed: " + ethLog.getError().getMessage(),
                    BlockchainIntegrityException.ErrorType.RPC_FAILURE);
        }
        if (ethLog.getLogs() == null) {
            return Optional.empty();
        }

        for (EthLog.LogResult<?> logResult : ethLog.getLogs()) {
            if (logResult instanceof EthLog.LogObject logObject) {
                Log log = logObject.get();
                if (log != null) {
                    return Optional.of(toBlockchainRecord(log));
                }
            }
        }

        return Optional.empty();
    }

    private AuditIntegrityCheckResponse.BlockchainRecord toBlockchainRecord(Log log) throws Exception {
        String transactionHash = log.getTransactionHash();
        Long blockNumber = log.getBlockNumber() == null ? null : log.getBlockNumber().longValue();
        // Decode timestamp directly from RecordAppended event data (the event includes uint256 timestamp)
        Long timestamp = decodeEventTimestamp(log);
        return new AuditIntegrityCheckResponse.BlockchainRecord(true, transactionHash, blockNumber, timestamp);
    }

    private Long decodeEventTimestamp(Log log) throws Exception {
        if (log.getData() == null || log.getData().isBlank()) {
            return null;
        }

        try {
            // Decode the non-indexed event parameters from log.data
            // RecordAppended event has: indexed bytes32 eventHash, uint256 timestamp, string eventType, indexed address source
            // Only non-indexed parameters are in log.data: uint256 timestamp (first 32 bytes) and string eventType (remaining)
            @SuppressWarnings("rawtypes")
            List paramTypes = new ArrayList();
            paramTypes.add(new TypeReference<Uint256>() {});  // timestamp (first non-indexed parameter)
            paramTypes.add(new TypeReference<Utf8String>() {}); // eventType (second non-indexed parameter)

            @SuppressWarnings("unchecked")

            List<Type> decoded = FunctionReturnDecoder.decode(log.getData(), paramTypes);

            if (decoded != null && !decoded.isEmpty() && decoded.get(0) instanceof Uint256) {
                return ((Uint256) decoded.get(0)).getValue().longValue();
            }
        } catch (Exception e) {
            LOGGER.debug("Failed to decode RecordAppended timestamp from event log; falling back to null", e);
            return null;
        }

        return null;
    }

    private byte[] parseEventHash(String eventHash) {
        String normalized = requireText(eventHash, "event hash is missing");
        if (normalized.startsWith("0x") || normalized.startsWith("0X")) {
            normalized = normalized.substring(2);
        }
        if (normalized.length() != 64 || !normalized.matches("[0-9a-fA-F]{64}")) {
            throw new BlockchainIntegrityException("event hash must be a 32-byte hex value",
                    BlockchainIntegrityException.ErrorType.CONFIGURATION);
        }
        return Numeric.hexStringToByteArray("0x" + normalized);
    }

    private String requireText(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new BlockchainIntegrityException(message,
                    BlockchainIntegrityException.ErrorType.CONFIGURATION);
        }
        return value.trim();
    }

    private DefaultBlockParameter resolveFromBlockParameter() {
        long deploymentBlock = props.getContractDeploymentBlock();
        if (deploymentBlock < 0) {
            throw new BlockchainIntegrityException("web3j.contract-deployment-block must be >= 0",
                    BlockchainIntegrityException.ErrorType.CONFIGURATION);
        }
        if (deploymentBlock == 0) {
            if (!isLocalRpcEndpoint(props.getClientAddress())) {
                throw new BlockchainIntegrityException(
                        "web3j.contract-deployment-block must be configured for non-local RPC endpoints",
                        BlockchainIntegrityException.ErrorType.CONFIGURATION);
            }
            return DefaultBlockParameterName.EARLIEST;
        }
        return DefaultBlockParameter.valueOf(BigInteger.valueOf(deploymentBlock));
    }

    private boolean isLocalRpcEndpoint(String clientAddress) {
        if (clientAddress == null || clientAddress.isBlank()) {
            return false;
        }

        try {
            URI uri = URI.create(clientAddress.trim());
            String host = uri.getHost();
            return "localhost".equalsIgnoreCase(host)
                    || "127.0.0.1".equals(host)
                    || "::1".equals(host);
        } catch (Exception ignored) {
            return false;
        }
    }

    private void validateContractAddress(String contractAddress) {
        if (!WalletUtils.isValidAddress(contractAddress)) {
            throw new BlockchainIntegrityException("web3j.contract-address is malformed",
                    BlockchainIntegrityException.ErrorType.CONFIGURATION);
        }
        // Reject zero-address (0x000...000) which is not a valid contract
        String normalized = contractAddress.toLowerCase();
        if ("0x0000000000000000000000000000000000000000".equals(normalized)) {
            throw new BlockchainIntegrityException("web3j.contract-address cannot be zero-address (0x000...000)",
                    BlockchainIntegrityException.ErrorType.CONFIGURATION);
        }
    }
}


package lt.satsyuk.distributed.audit.query.blockchain;

import lt.satsyuk.distributed.audit.query.api.AuditIntegrityCheckResponse;
import lt.satsyuk.distributed.audit.query.api.BlockchainIntegrityException;
import lt.satsyuk.distributed.audit.query.config.Web3jProperties;
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
import org.web3j.protocol.core.methods.response.EthBlock;
import org.web3j.protocol.core.methods.response.EthCall;
import org.web3j.protocol.core.methods.response.EthLog;
import org.web3j.protocol.core.methods.response.Log;
import org.web3j.protocol.core.methods.request.EthFilter;
import org.web3j.utils.Numeric;

import java.math.BigInteger;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

@Service
public class AuditLedgerBlockchainClient {

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
            throw new BlockchainIntegrityException("Blockchain call failed: empty eth_call response");
        }
        if (ethCall.hasError()) {
            throw new BlockchainIntegrityException("Blockchain call failed: " + ethCall.getError().getMessage());
        }

        List<Type> decoded = FunctionReturnDecoder.decode(ethCall.getValue(), function.getOutputParameters());
        if (decoded == null || decoded.isEmpty()) {
            throw new BlockchainIntegrityException("Blockchain call failed: eth_call returned empty value for isHashExists");
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
            throw new BlockchainIntegrityException("Blockchain log lookup failed: empty eth_getLogs response");
        }
        if (ethLog.hasError()) {
            throw new BlockchainIntegrityException("Blockchain log lookup failed: " + ethLog.getError().getMessage());
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
        Long timestamp = resolveBlockTimestamp(log.getBlockNumber());
        return new AuditIntegrityCheckResponse.BlockchainRecord(true, transactionHash, blockNumber, timestamp);
    }

    private Long resolveBlockTimestamp(BigInteger blockNumber) throws Exception {
        if (blockNumber == null) {
            return null;
        }

        EthBlock block = web3j.ethGetBlockByNumber(DefaultBlockParameter.valueOf(blockNumber), false).send();
        if (block == null) {
            throw new BlockchainIntegrityException("Blockchain block lookup failed: empty eth_getBlockByNumber response");
        }
        if (block.hasError()) {
            throw new BlockchainIntegrityException("Blockchain block lookup failed: " + block.getError().getMessage());
        }
        if (block.getBlock() == null || block.getBlock().getTimestamp() == null) {
            return null;
        }

        return block.getBlock().getTimestamp().longValue();
    }

    private byte[] parseEventHash(String eventHash) {
        String normalized = requireText(eventHash, "event hash is missing");
        if (normalized.startsWith("0x") || normalized.startsWith("0X")) {
            normalized = normalized.substring(2);
        }
        if (normalized.length() != 64 || !normalized.matches("[0-9a-fA-F]{64}")) {
            throw new BlockchainIntegrityException("event hash must be a 32-byte hex value");
        }
        return Numeric.hexStringToByteArray("0x" + normalized);
    }

    private String requireText(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new BlockchainIntegrityException(message);
        }
        return value.trim();
    }

    private DefaultBlockParameter resolveFromBlockParameter() {
        long deploymentBlock = props.getContractDeploymentBlock();
        if (deploymentBlock < 0) {
            throw new BlockchainIntegrityException("web3j.contract-deployment-block must be >= 0");
        }
        if (deploymentBlock == 0) {
            return DefaultBlockParameterName.EARLIEST;
        }
        return DefaultBlockParameter.valueOf(BigInteger.valueOf(deploymentBlock));
    }

    private void validateContractAddress(String contractAddress) {
        if (!WalletUtils.isValidAddress(contractAddress)) {
            throw new BlockchainIntegrityException("web3j.contract-address is malformed");
        }
        // Reject zero-address (0x000...000) which is not a valid contract
        String normalized = contractAddress.toLowerCase();
        if ("0x0000000000000000000000000000000000000000".equals(normalized)) {
            throw new BlockchainIntegrityException("web3j.contract-address cannot be zero-address (0x000...000)");
        }
    }
}


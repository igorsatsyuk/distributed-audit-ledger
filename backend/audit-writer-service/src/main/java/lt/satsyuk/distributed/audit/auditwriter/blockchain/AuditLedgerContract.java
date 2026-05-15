package lt.satsyuk.distributed.audit.auditwriter.blockchain;

import org.web3j.abi.TypeReference;
import org.web3j.abi.datatypes.Address;
import org.web3j.abi.datatypes.Function;
import org.web3j.abi.datatypes.Utf8String;
import org.web3j.abi.datatypes.generated.Bytes32;
import org.web3j.abi.datatypes.generated.Uint256;
import org.web3j.crypto.Credentials;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.methods.response.TransactionReceipt;
import org.web3j.tx.Contract;
import org.web3j.tx.gas.ContractGasProvider;

import java.math.BigInteger;
import java.util.Arrays;
import java.util.Collections;

/**
 * Web3j contract wrapper for {@code AuditLedger.sol}.
 *
 * <p>Provides typed Java bindings for the on-chain functions used by
 * {@link lt.satsyuk.distributed.audit.auditwriter.service.BlockchainWriterService}:
 * <ul>
 *   <li>{@link #appendAuditRecord} — anchors an event hash</li>
 *   <li>{@link #isHashExists} — checks for duplicate hashes</li>
 * </ul>
 *
 * <p>This class was written manually from the ABI to avoid a Hardhat code-generation
 * build step. Regenerate with {@code web3j generate} if the ABI changes.
 */
public class AuditLedgerContract extends Contract {

    /** Solidity bytecode is not needed for loading an already-deployed contract. */
    private static final String BINARY = "";

    public static final String FUNC_APPEND_AUDIT_RECORD = "appendAuditRecord";
    public static final String FUNC_IS_HASH_EXISTS      = "isHashExists";

    protected AuditLedgerContract(String contractAddress,
                                   Web3j web3j,
                                   Credentials credentials,
                                   ContractGasProvider gasProvider) {
        super(BINARY, contractAddress, web3j, credentials, gasProvider);
    }

    // -------------------------------------------------------------------------
    // Factory
    // -------------------------------------------------------------------------

    /**
     * Loads an already-deployed {@code AuditLedger} contract at {@code contractAddress}.
     */
    public static AuditLedgerContract load(String contractAddress,
                                            Web3j web3j,
                                            Credentials credentials,
                                            ContractGasProvider gasProvider) {
        return new AuditLedgerContract(contractAddress, web3j, credentials, gasProvider);
    }

    // -------------------------------------------------------------------------
    // Write functions
    // -------------------------------------------------------------------------

    /**
     * Calls {@code appendAuditRecord(bytes32, uint256, string, address)} on-chain.
     *
     * @param hash      SHA-256 event hash (32 bytes)
     * @param timestamp epoch seconds of the event
     * @param eventType domain event type string, e.g. {@code "USER_LOGGED_IN"}
     * @param source    Ethereum address of the sending service account
     * @return the mined {@link TransactionReceipt}
     */
    public TransactionReceipt appendAuditRecord(byte[] hash,
                                                 BigInteger timestamp,
                                                 String eventType,
                                                 String source) throws Exception {
        final Function function = new Function(
                FUNC_APPEND_AUDIT_RECORD,
                Arrays.asList(
                        new Bytes32(hash),
                        new Uint256(timestamp),
                        new Utf8String(eventType),
                        new Address(source)
                ),
                Collections.emptyList()
        );
        return executeRemoteCallTransaction(function).send();
    }

    // -------------------------------------------------------------------------
    // Read functions
    // -------------------------------------------------------------------------

    /**
     * Calls {@code isHashExists(bytes32)} — a view function (no state change, no gas for eth_call).
     *
     * @param hash 32-byte hash to check
     * @return {@code true} if the hash has already been recorded on-chain
     */
    public boolean isHashExists(byte[] hash) throws Exception {
        final Function function = new Function(
                FUNC_IS_HASH_EXISTS,
                Collections.singletonList(new Bytes32(hash)),
                Collections.singletonList(new TypeReference<org.web3j.abi.datatypes.Bool>() {})
        );
        return executeRemoteCallSingleValueReturn(function, Boolean.class).send();
    }
}


package lt.satsyuk.distributed.audit.auditwriter.blockchain;

import org.junit.jupiter.api.Test;
import org.web3j.abi.FunctionEncoder;
import org.web3j.abi.datatypes.Function;
import org.web3j.crypto.Hash;

import java.math.BigInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies that function signatures and ABI encodings used by the manual wrapper match the ABI.
 *
 * <p>Selector tests guard against a renamed function constant.
 * Full-encoding tests guard against parameter-list typos (wrong order, wrong type) by
 * exercising the static builder methods used by the wrapper and verifying encoded output
 * length and selector prefix.
 */
class AuditLedgerContractEncodingTest {

    @Test
    void appendAuditRecord_selectorMatchesAbiSignature() {
        String signature = "appendAuditRecord(bytes32,uint256,string,address)";
        String selector = Hash.sha3String(signature).substring(0, 10);

        assertThat(selector).isEqualTo("0x5002047e");
    }

    @Test
    void isHashExists_selectorMatchesAbiSignature() {
        String signature = "isHashExists(bytes32)";
        String selector = Hash.sha3String(signature).substring(0, 10);

        assertThat(selector).isEqualTo("0x3bd4c457");
    }

    @Test
    void owner_selectorMatchesAbiSignature() {
        Function ownerFn = AuditLedgerContract.buildOwnerFunction();
        String encoded = FunctionEncoder.encode(ownerFn);

        assertThat(ownerFn.getName()).isEqualTo(AuditLedgerContract.FUNC_OWNER);
        assertThat(encoded).startsWith("0x8da5cb5b");
    }

    /**
     * Exercises the {@code appendAuditRecord} ABI wrapper by encoding a real call and
     * verifying that it starts with the correct 4-byte selector and has the expected
     * total length for the given argument types.
     *
     * <p>Expected layout (196 bytes, 394 hex chars inc. "0x"):
     * 4 selector + 32 bytes32 + 32 uint256 + 32 string-offset + 32 address +
     * 32 string-length + 32 string-data-padded
     */
    @Test
    void appendAuditRecord_encodedCallHasCorrectSelectorAndLength() {
        byte[] hash = new byte[32]; // all zeros
        BigInteger timestamp = BigInteger.valueOf(1_704_067_200L);
        String eventType = "USER_LOGGED_IN"; // 14 chars → padded to 32 bytes
        String source = "0x0000000000000000000000000000000000000001";

        Function fn = AuditLedgerContract.buildAppendAuditRecordFunction(hash, timestamp, eventType, source);
        String encoded = FunctionEncoder.encode(fn);

        assertThat(encoded)
                .as("call must start with appendAuditRecord selector")
                .startsWith("0x5002047e");

        // 0x + 8 (selector) + 64 (bytes32) + 64 (uint256) + 64 (offset) + 64 (address)
        // + 64 (string length) + 64 (string data padded) = 2 + 8 + 384 = 394 chars
        assertThat(encoded)
                .as("encoded call length must match ABI parameter encoding for (bytes32,uint256,string,address)")
                .hasSize(394);
    }

    /**
     * Exercises the {@code isHashExists} ABI wrapper by encoding a real call and verifying
     * that it starts with the correct 4-byte selector and has the expected total length.
     *
     * <p>Expected layout (36 bytes, 74 hex chars inc. "0x"):
     * 4 selector + 32 bytes32
     */
    @Test
    void isHashExists_encodedCallHasCorrectSelectorAndLength() {
        byte[] hash = new byte[32];

        Function fn = AuditLedgerContract.buildIsHashExistsFunction(hash);
        String encoded = FunctionEncoder.encode(fn);

        assertThat(encoded)
                .as("call must start with isHashExists selector")
                .startsWith("0x3bd4c457");

        // 0x + 8 (selector) + 64 (bytes32) = 74 chars
        assertThat(encoded)
                .as("encoded call length must match ABI parameter encoding for (bytes32)")
                .hasSize(74);
    }
}

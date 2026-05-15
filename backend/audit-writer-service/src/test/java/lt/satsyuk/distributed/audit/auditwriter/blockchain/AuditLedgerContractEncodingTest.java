package lt.satsyuk.distributed.audit.auditwriter.blockchain;

import org.junit.jupiter.api.Test;
import org.web3j.crypto.Hash;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies that function signatures used by the manual wrapper match the ABI.
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
}


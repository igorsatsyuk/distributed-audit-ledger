package lt.satsyuk.distributed.audit.auditwriter.blockchain;

import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Non-Docker sync check for the committed contract test resources.
 *
 * <p>This test is intentionally separate from Testcontainers-based tests so it always
 * runs in Surefire, even on machines where Docker is unavailable.
 */
class AuditLedgerBytecodeSyncTest {

    @Test
    void auditLedgerBytecodeSnapshot_staysInSyncWithAuditLedgerSolSource() throws Exception {
        Path sourcePath = locateAuditLedgerSource();
        if (sourcePath == null) {
            throw new IllegalStateException("Cannot find blockchain/contracts/AuditLedger.sol from current working directory");
        }

        String expectedHash;
        try (InputStream stream = AuditLedgerBytecodeSyncTest.class.getResourceAsStream("/AuditLedger.sol.sha256")) {
            if (stream == null) {
                throw new IllegalStateException(
                        "Classpath resource /AuditLedger.sol.sha256 not found. "
                                + "Update test resources when AuditLedger.sol changes.");
            }
            expectedHash = new String(stream.readAllBytes(), StandardCharsets.UTF_8).strip();
        }

        String actualHash = sha256HexNormalized(Files.readString(sourcePath));
        assertThat(actualHash)
                .as("AuditLedger.sol hash marker must match current Solidity source")
                .isEqualTo(expectedHash);
    }

    private static Path locateAuditLedgerSource() {
        Path current = Path.of("").toAbsolutePath().normalize();
        for (int i = 0; i < 8 && current != null; i++) {
            Path candidate = current
                    .resolve("blockchain")
                    .resolve("contracts")
                    .resolve("AuditLedger.sol");
            if (Files.exists(candidate)) {
                return candidate;
            }
            current = current.getParent();
        }
        return null;
    }

    private static String sha256HexNormalized(String source) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            String normalized = source.replace("\r\n", "\n");
            byte[] hash = digest.digest(normalized.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 algorithm not available", ex);
        }
    }
}


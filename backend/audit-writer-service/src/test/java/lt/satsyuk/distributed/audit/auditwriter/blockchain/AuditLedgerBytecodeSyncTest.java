package lt.satsyuk.distributed.audit.auditwriter.blockchain;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
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

    private static final ObjectMapper JSON = JsonMapper.builder().build();

    @Test
    void auditLedgerSnapshots_stayInSyncWithCurrentSoliditySourceAndCommittedCompileSnapshot() throws Exception {
        Path repoRoot = locateRepoRoot();
        Path sourcePath = repoRoot.resolve("blockchain").resolve("contracts").resolve("AuditLedger.sol");

        assertThat(sourcePath)
                .as("AuditLedger Solidity source must exist")
                .exists();

        String currentSource = Files.readString(sourcePath);
        String normalizedCurrentSource = normalizeLineEndings(currentSource);

        String expectedHash = readClasspathResource("/AuditLedger.sol.sha256");
        String actualHash = sha256HexNormalized(currentSource);
        assertThat(actualHash)
                .as("AuditLedger.sol hash marker must match current Solidity source")
                .isEqualTo(expectedHash);

        JsonNode compileSnapshot = JSON.readTree(readClasspathResource("/AuditLedger.compile-snapshot.json"));
        assertThat(compileSnapshot.path("sourceName").asText())
                .as("compile snapshot must point to AuditLedger.sol")
                .isEqualTo("contracts/AuditLedger.sol");
        assertThat(compileSnapshot.path("contractName").asText())
                .as("compile snapshot must target the AuditLedger contract")
                .isEqualTo("AuditLedger");

        String compiledSource = compileSnapshot.path("source")
                .asText(null);
        assertThat(compiledSource)
                .as("Committed compile snapshot must include contracts/AuditLedger.sol source content")
                .isNotBlank();
        assertThat(normalizeLineEndings(compiledSource))
                .as("Committed compile snapshot must be regenerated from the current AuditLedger.sol source")
                .isEqualTo(normalizedCurrentSource);

        String compiledBytecode = compileSnapshot.path("bytecode").asText(null);
        assertThat(compiledBytecode)
                .as("Committed compile snapshot must include AuditLedger bytecode")
                .isNotBlank();

        String committedBytecode = readClasspathResource("/AuditLedger.bytecode");
        assertThat(normalizeHexBytecode(committedBytecode))
                .as("Committed AuditLedger.bytecode snapshot must match the committed compile snapshot bytecode")
                .isEqualTo(normalizeHexBytecode(compiledBytecode));
    }

    private static Path locateRepoRoot() {
        Path current = Path.of("").toAbsolutePath().normalize();
        for (int i = 0; i < 8 && current != null; i++) {
            Path candidate = current.resolve("blockchain").resolve("contracts").resolve("AuditLedger.sol");
            if (Files.exists(candidate)) {
                return current;
            }
            current = current.getParent();
        }
        throw new IllegalStateException("Cannot find repository root containing blockchain/contracts/AuditLedger.sol");
    }


    private static String readClasspathResource(String resourcePath) throws Exception {
        try (InputStream stream = AuditLedgerBytecodeSyncTest.class.getResourceAsStream(resourcePath)) {
            if (stream == null) {
                throw new IllegalStateException("Classpath resource " + resourcePath + " not found");
            }
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8).strip();
        }
    }

    private static String normalizeLineEndings(String value) {
        return value.replace("\r\n", "\n");
    }

    private static String normalizeHexBytecode(String bytecode) {
        String normalized = bytecode.strip();
        return normalized.startsWith("0x") ? normalized : "0x" + normalized;
    }

    private static String sha256HexNormalized(String source) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            String normalized = normalizeLineEndings(source);
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

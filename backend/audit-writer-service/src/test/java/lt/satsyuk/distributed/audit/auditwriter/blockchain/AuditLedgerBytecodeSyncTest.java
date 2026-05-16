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
import java.util.Comparator;
import java.util.stream.Stream;

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
    void auditLedgerSnapshots_stayInSyncWithCurrentSoliditySourceAndArtifact() throws Exception {
        Path repoRoot = locateRepoRoot();
        Path sourcePath = repoRoot.resolve("blockchain").resolve("contracts").resolve("AuditLedger.sol");
        Path artifactPath = repoRoot.resolve("blockchain").resolve("artifacts")
                .resolve("contracts").resolve("AuditLedger.sol").resolve("AuditLedger.json");
        Path buildInfoPath = locateBuildInfo(repoRoot);

        assertThat(sourcePath)
                .as("AuditLedger Solidity source must exist")
                .exists();
        assertThat(artifactPath)
                .as("Hardhat artifact for AuditLedger must exist")
                .exists();
        assertThat(buildInfoPath)
                .as("Hardhat build-info for AuditLedger must exist")
                .exists();

        String currentSource = Files.readString(sourcePath);
        String normalizedCurrentSource = normalizeLineEndings(currentSource);

        String expectedHash = readClasspathResource("/AuditLedger.sol.sha256");
        String actualHash = sha256HexNormalized(currentSource);
        assertThat(actualHash)
                .as("AuditLedger.sol hash marker must match current Solidity source")
                .isEqualTo(expectedHash);

        JsonNode buildInfo = JSON.readTree(Files.readString(buildInfoPath));
        String compiledSource = buildInfo.path("input")
                .path("sources")
                .path("contracts/AuditLedger.sol")
                .path("content")
                .asText(null);
        assertThat(compiledSource)
                .as("Hardhat build-info must include contracts/AuditLedger.sol source content")
                .isNotBlank();
        assertThat(normalizeLineEndings(compiledSource))
                .as("Hardhat artifacts must be rebuilt from the current AuditLedger.sol source")
                .isEqualTo(normalizedCurrentSource);

        JsonNode artifact = JSON.readTree(Files.readString(artifactPath));
        String artifactBytecode = artifact.path("bytecode").asText(null);
        assertThat(artifact.path("sourceName").asText())
                .as("artifact must point to AuditLedger.sol")
                .isEqualTo("contracts/AuditLedger.sol");
        assertThat(artifactBytecode)
                .as("Hardhat artifact bytecode must be present")
                .isNotBlank();

        String committedBytecode = readClasspathResource("/AuditLedger.bytecode");
        assertThat(normalizeHexBytecode(committedBytecode))
                .as("Committed AuditLedger.bytecode snapshot must match the current Hardhat artifact bytecode")
                .isEqualTo(normalizeHexBytecode(artifactBytecode));
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

    private static Path locateBuildInfo(Path repoRoot) throws Exception {
        Path buildInfoDir = repoRoot.resolve("blockchain").resolve("artifacts").resolve("build-info");
        if (!Files.isDirectory(buildInfoDir)) {
            throw new IllegalStateException("Hardhat build-info directory not found: " + buildInfoDir);
        }
        try (Stream<Path> files = Files.list(buildInfoDir)) {
            return files
                    .filter(path -> path.getFileName().toString().endsWith(".json"))
                    .max(Comparator.comparing(path -> path.getFileName().toString()))
                    .orElseThrow(() -> new IllegalStateException(
                            "No Hardhat build-info JSON files found under " + buildInfoDir));
        }
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

package lt.satsyuk.distributed.audit.auditwriter.service;

import lt.satsyuk.distributed.audit.auditwriter.config.JacksonConfig;
import lt.satsyuk.distributed.audit.event.EventType;
import lt.satsyuk.distributed.audit.event.UserLoggedInEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link HashCalculationService}.
 */
class HashCalculationServiceTest {

    private HashCalculationService hashService;
    private JacksonConfig jacksonConfig;

    @BeforeEach
    void setUp() {
        jacksonConfig = new JacksonConfig();
        hashService = new HashCalculationService(jacksonConfig.objectMapper());
    }

    @Test
    void computeHash_returnsExactly32Bytes() {
        UserLoggedInEvent event = UserLoggedInEvent.of("user1", "127.0.0.1", "test-agent");

        byte[] hash = hashService.computeHash(event);

        assertThat(hash).hasSize(32);
    }

    @Test
    void computeHash_isDeterministic() {
        UserLoggedInEvent event = UserLoggedInEvent.of("user42", null, null);

        byte[] hash1 = hashService.computeHash(event);
        byte[] hash2 = hashService.computeHash(event);

        assertThat(hash1).isEqualTo(hash2);
    }

    @Test
    void computeHash_differsForDifferentEvents() {
        UserLoggedInEvent e1 = UserLoggedInEvent.of("user1", null, null);
        UserLoggedInEvent e2 = UserLoggedInEvent.of("user2", null, null);
        // eventId is UUID — guaranteed different even without userId difference
        assertThat(hashService.computeHash(e1)).isNotEqualTo(hashService.computeHash(e2));
    }

    @Test
    void toHexString_producesLowercase64CharString() {
        byte[] bytes = new byte[32];
        for (int i = 0; i < 32; i++) bytes[i] = (byte) i;

        String hex = HashCalculationService.toHexString(bytes);

        assertThat(hex).hasSize(64).matches("[0-9a-f]+");
    }

    /**
     * Verifies that the audit-writer hash serialization produces deterministic output
     * using a fixed test event.
     *
     * <p>This test ensures that the same event always produces the same hash. It uses
     * the audit-writer's own {@code JacksonConfig} for both sides, so it cannot detect
     * misalignment with event-store's ObjectMapper configuration (e.g., null field ordering,
     * date serialization format).  Such misalignments should be caught by cross-service
     * integration tests that verify hash agreement on the same Kafka event before/after
     * storage and anchoring.  This test protects against accidental local changes that
     * would break determinism.
     */
    @Test
    void computeHash_producesConsistentOutputForFixedEvent() throws Exception {
        // Fixed event so the test is deterministic and independent of UUID randomness
        UserLoggedInEvent event = UserLoggedInEvent.builder()
                .eventId("00000000-0000-0000-0000-000000000001")
                .eventType(EventType.USER_LOGGED_IN)
                .occurredAt(Instant.parse("2024-01-01T00:00:00Z"))
                .sourceService("command-service")
                .userId("test-user")
                .ipAddress(null)
                .userAgent(null)
                .build();

        // Replicate serialization path: mapper.writeValueAsString(event)
        String json = jacksonConfig.objectMapper().writeValueAsString(event);
        byte[] expectedBytes = MessageDigest.getInstance("SHA-256")
                .digest(json.getBytes(StandardCharsets.UTF_8));
        String expectedHex = HashCalculationService.toHexString(expectedBytes);

        // Audit-writer path
        byte[] auditBytes = hashService.computeHash(event);
        String auditHex = HashCalculationService.toHexString(auditBytes);

        // Both should agree because they use the same ObjectMapper instance
        assertThat(auditHex)
                .as("audit-writer SHA-256 hash must be deterministic for the same event. "
                        + "If this fails, check that JacksonConfig has not changed. "
                        + "JSON was: %s", json)
                .isEqualTo(expectedHex);
    }
}

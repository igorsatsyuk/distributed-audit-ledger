package lt.satsyuk.distributed.audit.auditwriter.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import lt.satsyuk.distributed.audit.auditwriter.config.JacksonConfig;
import lt.satsyuk.distributed.audit.contracts.config.CanonicalObjectMapperFactory;
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
        // Events have different userIds AND different random UUIDs, so hashes must differ
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
     * using a fixed test event with a hard-coded expected SHA-256 digest.
     *
     * <p>The expected hex value was captured from a passing test run and pinned here.
     * If Jackson configuration diverges (e.g., null-field handling, date serialization
     * format), this test will fail and flag the potential cross-service integrity issue.
     *
     * <p>Note: full cross-service hash alignment (audit-writer vs event-store) is best
     * verified by an integration test that exchanges events over Kafka between both
     * services; this test guards against accidental local ObjectMapper changes only.
     */
    @Test
    void computeHash_producesConsistentOutputForFixedEvent() throws Exception {
        // canonical JSON: {"eventId":"00000000-0000-0000-0000-000000000001","eventType":
        // "USER_LOGGED_IN","occurredAt":1704067200.000000000,"sourceService":"command-service",
        // "userId":"test-user","ipAddress":null,"userAgent":null}
        UserLoggedInEvent event = UserLoggedInEvent.builder()
                .eventId("00000000-0000-0000-0000-000000000001")
                .eventType(EventType.USER_LOGGED_IN)
                .occurredAt(Instant.parse("2024-01-01T00:00:00Z"))
                .sourceService("command-service")
                .userId("test-user")
                .ipAddress(null)
                .userAgent(null)
                .build();

        // Hard-coded expected digest — captured with JacksonConfig as of this commit.
        // A change to this value signals a serialization format regression that would
        // break cross-ledger hash verification.
        String expectedHex = "9dba01dcf7dc7be4dcd6b9d8571ba72f242a04efbeeeaf87ad59c3b973bb9583";

        byte[] auditBytes = hashService.computeHash(event);
        String auditHex = HashCalculationService.toHexString(auditBytes);

        assertThat(auditHex)
                .as("SHA-256 hash must match the pinned expected value; "
                        + "if JacksonConfig changed, re-evaluate both this baseline and "
                        + "cross-service hash compatibility")
                .isEqualTo(expectedHex);
    }

    @Test
    void computeHash_matchesEventStoreBaselineMapperAndAlgorithm() throws Exception {
        UserLoggedInEvent event = UserLoggedInEvent.builder()
                .eventId("00000000-0000-0000-0000-00000000000a")
                .eventType(EventType.USER_LOGGED_IN)
                .occurredAt(Instant.parse("2024-02-01T12:00:00Z"))
                .sourceService("command-service")
                .userId("baseline-user")
                .ipAddress("127.0.0.1")
                .userAgent("ua")
                .build();

        // Uses the shared CanonicalObjectMapperFactory (the same factory used by both
        // event-store-service and audit-writer-service at runtime), so any change to
        // the shared factory will be reflected on both sides of this comparison.
        ObjectMapper eventStoreMapper = CanonicalObjectMapperFactory.create();
        String payloadJson = eventStoreMapper.writeValueAsString(event);
        byte[] expected = MessageDigest.getInstance("SHA-256")
                .digest(payloadJson.getBytes(StandardCharsets.UTF_8));

        assertThat(hashService.computeHash(event)).isEqualTo(expected);
    }
}

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
     * Verifies that the audit-writer hash output is byte-for-byte identical to what
     * the event-store {@code EventHashService.sha256Hex(objectMapper.writeValueAsString(event))}
     * would produce for the same event.
     *
     * <p>Both services use {@code JsonMapper.builder().findAndAddModules().build()}.  If their
     * ObjectMapper configurations ever diverge (e.g. null-ordering, date format), this test
     * will catch the mismatch before it breaks cross-ledger integrity in production.
     */
    @Test
    void computeHash_matchesEventStoreHashForSameSerializedEvent() throws Exception {
        // Fixed event so the test is independent of UUID randomness
        UserLoggedInEvent event = UserLoggedInEvent.builder()
                .eventId("00000000-0000-0000-0000-000000000001")
                .eventType(EventType.USER_LOGGED_IN)
                .occurredAt(Instant.parse("2024-01-01T00:00:00Z"))
                .sourceService("command-service")
                .userId("test-user")
                .ipAddress(null)
                .userAgent(null)
                .build();

        // Replicate event-store EventHashService.sha256Hex(objectMapper.writeValueAsString(event))
        String json = jacksonConfig.objectMapper().writeValueAsString(event);
        byte[] expectedBytes = MessageDigest.getInstance("SHA-256")
                .digest(json.getBytes(StandardCharsets.UTF_8));
        String expectedHex = HashCalculationService.toHexString(expectedBytes);

        // Audit-writer path
        byte[] auditBytes = hashService.computeHash(event);
        String auditHex = HashCalculationService.toHexString(auditBytes);

        assertThat(auditHex)
                .as("audit-writer SHA-256 must match event-store sha256Hex for the same serialized event; "
                        + "serialized JSON was: %s", json)
                .isEqualTo(expectedHex);
    }
}

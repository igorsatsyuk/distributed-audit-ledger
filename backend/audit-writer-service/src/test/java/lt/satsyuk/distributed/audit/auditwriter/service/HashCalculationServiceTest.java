package lt.satsyuk.distributed.audit.auditwriter.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lt.satsyuk.distributed.audit.event.UserLoggedInEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link HashCalculationService}.
 */
class HashCalculationServiceTest {

    private HashCalculationService hashService;

    @BeforeEach
    void setUp() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        hashService = new HashCalculationService(mapper);
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
}


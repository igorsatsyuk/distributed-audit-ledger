package lt.satsyuk.distributed.audit.eventstore.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class EventHashServiceTest {

    private final EventHashService eventHashService = new EventHashService();

    @Test
    void sha256HexReturnsExpectedDigest() {
        String hash = eventHashService.sha256Hex("abc");

        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad", hash);
    }
}


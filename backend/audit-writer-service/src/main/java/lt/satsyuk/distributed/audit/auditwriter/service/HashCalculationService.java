package lt.satsyuk.distributed.audit.auditwriter.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import lt.satsyuk.distributed.audit.event.AuditEvent;
import com.fasterxml.jackson.core.JsonProcessingException;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * Computes a SHA-256 hash for a given {@link AuditEvent}.
 *
 * <p>The hash is derived from the JSON serialisation of the event produced by
 * the injected Jackson {@link com.fasterxml.jackson.databind.ObjectMapper}.
 * The mapper must be configured identically in every service that computes or
 * verifies this hash (e.g. {@code event-store-service}); any serialisation
 * difference produces a different hash and breaks cross-ledger integrity.
 *
 * <p>The output is deterministic for the same Java object serialised by the
 * same mapper configuration, but it is <em>not</em> guaranteed to be
 * field-order–independent unless the mapper explicitly enables canonical ordering.
 */
@Service
public class HashCalculationService {

    private static final int HEX_CHARS_PER_BYTE = 2;
    private static final String SHA_256 = "SHA-256";

    private final ObjectMapper objectMapper;

    public HashCalculationService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * Serialises {@code event} to JSON and returns its SHA-256 digest as a
     * 32-byte array suitable for passing to the Solidity {@code bytes32} parameter.
     *
     * @param event the domain event to hash
     * @return 32-byte SHA-256 digest
     * @throws RuntimeException if JSON serialisation or digest computation fails
     */
    public byte[] computeHash(AuditEvent event) {
        try {
            String json = objectMapper.writeValueAsString(event);
            MessageDigest digest = MessageDigest.getInstance(SHA_256);
            return digest.digest(json.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm not available", e);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to compute hash for event " + event.getEventId(), e);
        }
    }

    /**
     * Converts a raw 32-byte hash to its lowercase hex string representation,
     * e.g. for logging or DB storage.
     */
    public static String toHexString(byte[] hash) {
        StringBuilder sb = new StringBuilder(hash.length * HEX_CHARS_PER_BYTE);
        for (byte b : hash) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }
}


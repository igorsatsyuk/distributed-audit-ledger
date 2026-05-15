package lt.satsyuk.distributed.audit.auditwriter.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import lt.satsyuk.distributed.audit.event.AuditEvent;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * Computes a deterministic SHA-256 hash for a given {@link AuditEvent}.
 *
 * <p>The hash is derived from the canonical JSON serialisation of the event
 * produced by Jackson.  Using JSON (rather than, say, {@code toString()}) ensures
 * that the hash is stable across JVM restarts and independent of field ordering
 * in the Java object graph.
 */
@Service
public class HashCalculationService {

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
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return digest.digest(json.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm not available", e);
        } catch (Exception e) {
            throw new RuntimeException("Failed to compute hash for event " + event.getEventId(), e);
        }
    }

    /**
     * Converts a raw 32-byte hash to its lowercase hex string representation,
     * e.g. for logging or DB storage.
     */
    public static String toHexString(byte[] hash) {
        StringBuilder sb = new StringBuilder(hash.length * 2);
        for (byte b : hash) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }
}


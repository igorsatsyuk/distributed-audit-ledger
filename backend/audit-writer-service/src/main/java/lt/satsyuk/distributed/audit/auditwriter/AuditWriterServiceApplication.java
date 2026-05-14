package lt.satsyuk.distributed.audit.auditwriter;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Audit Writer Service — entry point.
 *
 * <p>Subscribes to Kafka domain events, computes a SHA-256 hash of each
 * event payload, and anchors the hash on the Ganache blockchain by calling
 * {@code AuditLedger.appendAuditRecord(...)}. Runs on port 8083.
 */
@SpringBootApplication
public class AuditWriterServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(AuditWriterServiceApplication.class, args);
    }
}


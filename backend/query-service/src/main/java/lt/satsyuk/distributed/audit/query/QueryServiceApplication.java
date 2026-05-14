package lt.satsyuk.distributed.audit.query;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Query Service — entry point.
 *
 * <p>Read-side service that exposes the audit log as paginated REST endpoints.
 * Reads directly from the shared {@code audit.events} PostgreSQL table (same DB
 * as event-store-service, read-only access). Runs on port 8084.
 */
@SpringBootApplication
public class QueryServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(QueryServiceApplication.class, args);
    }
}


package lt.satsyuk.distributed.audit.eventstore;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.kafka.annotation.EnableKafka;

/**
 * Event Store Service — entry point.
 *
 * <p>Consumes domain events from Kafka topic {@code user.login.events},
 * computes a SHA-256 hash of each event payload and persists the record
 * to the {@code audit.events} PostgreSQL table. Runs on port 8082.
 */
@SpringBootApplication
@EnableKafka
public class EventStoreServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(EventStoreServiceApplication.class, args);
    }
}


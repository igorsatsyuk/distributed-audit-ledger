package lt.satsyuk.distributed.audit.auditwriter.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Jackson setup for event serialization during hash calculation.
 *
 * <p>Uses {@code JsonMapper.builder().findAndAddModules()} — the same configuration
 * as {@code event-store-service} — so the SHA-256 hash computed here from an {@link AuditEvent}
 * is deterministic and (in theory) byte-for-byte identical to the {@code event_hash} stored
 * in the DB if both services serialize the event identically.
 *
 * <p>Note: Kafka message serialization (consumer/producer) is configured separately
 * in {@code KafkaListenerConfig}; it does not use this {@code ObjectMapper} bean.
 */
@Configuration
public class JacksonConfig {

    @Bean
    public ObjectMapper objectMapper() {
        return JsonMapper.builder().findAndAddModules().build();
    }
}


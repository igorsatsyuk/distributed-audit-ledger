package lt.satsyuk.distributed.audit.auditwriter.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import lt.satsyuk.distributed.audit.contracts.config.CanonicalObjectMapperFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Jackson setup for event serialization during hash calculation.
 *
 * <p>Delegates to the shared canonical {@link ObjectMapper} factory so audit-writer and
 * event-store serialize events identically before computing or persisting hashes.
 *
 * <p>Note: Kafka message serialization (consumer/producer) is configured separately
 * in {@code KafkaListenerConfig}; it does not use this {@code ObjectMapper} bean.
 */
@Configuration
public class JacksonConfig {

    @Bean
    public ObjectMapper objectMapper() {
        return CanonicalObjectMapperFactory.create();
    }
}


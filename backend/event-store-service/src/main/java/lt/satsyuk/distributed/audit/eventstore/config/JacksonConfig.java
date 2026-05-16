package lt.satsyuk.distributed.audit.eventstore.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import lt.satsyuk.distributed.audit.contracts.config.CanonicalObjectMapperFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Jackson setup for event serialization used when computing persisted event hashes.
 * Delegates to the shared canonical {@link ObjectMapper} factory used by audit-writer.
 */
@Configuration
public class JacksonConfig {

    @Bean
    public ObjectMapper objectMapper() {
        return CanonicalObjectMapperFactory.create();
    }
}


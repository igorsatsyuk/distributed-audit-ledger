package lt.satsyuk.distributed.audit.eventstore.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import lt.satsyuk.distributed.audit.contracts.config.CanonicalObjectMapperFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Jackson setup for event serialization used when computing persisted event hashes.
 * Delegates to the shared canonical {@link ObjectMapper} factory used by audit-writer.
 *
 * <p>Compatibility note: existing rows generated with legacy mapper settings may contain
 * different {@code event_hash} values for the same logical payload. Apply the backfill plan in
 * {@code docs/EVENT_HASH_CANONICAL_MIGRATION.md} before enabling strict DB-vs-chain verification
 * in environments that already have historical data.
 */
@Configuration
public class JacksonConfig {

    @Bean
    public ObjectMapper objectMapper() {
        return CanonicalObjectMapperFactory.create();
    }
}


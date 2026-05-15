package lt.satsyuk.distributed.audit.auditwriter.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Jackson setup used by hash calculation and Kafka JSON payloads.
 *
 * <p>Uses {@code JsonMapper.builder().findAndAddModules()} — the same configuration
 * as {@code event-store-service} — so the SHA-256 hash computed here from a Kafka
 * event is byte-for-byte identical to the {@code event_hash} stored in the DB,
 * enabling cross-ledger integrity verification.
 */
@Configuration
public class JacksonConfig {

    @Bean
    public ObjectMapper objectMapper() {
        return JsonMapper.builder().findAndAddModules().build();
    }
}


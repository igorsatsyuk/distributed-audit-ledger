package lt.satsyuk.distributed.audit.contracts.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

/**
 * Shared factory for the canonical Jackson {@link ObjectMapper} used by services that
 * must serialize audit events byte-for-byte identically before hashing them.
 *
 * <p>Modules are registered <em>explicitly</em> (rather than via {@code findAndAddModules()})
 * so the serialised JSON — and therefore the SHA-256 hash — is the same regardless of which
 * Jackson modules happen to be on each service's runtime classpath.  Adding an optional
 * Jackson module to only one service must not silently change its event hashes.
 *
 * <p>Required module: {@link JavaTimeModule} for {@link java.time.Instant} serialization.
 * Property and map-entry ordering are pinned so the serialized JSON stays deterministic
 * across services and runtime/library upgrades.
 */
public final class CanonicalObjectMapperFactory {

    private CanonicalObjectMapperFactory() {
    }

    public static ObjectMapper create() {
        return JsonMapper.builder()
                .addModule(new JavaTimeModule())
                .enable(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY)
                .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS)
                .build();
    }
}

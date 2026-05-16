package lt.satsyuk.distributed.audit.contracts.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;

/**
 * Shared factory for the canonical Jackson {@link ObjectMapper} used by services that
 * must serialize audit events byte-for-byte identically before hashing them.
 */
public final class CanonicalObjectMapperFactory {

    private CanonicalObjectMapperFactory() {
    }

    public static ObjectMapper create() {
        return JsonMapper.builder().findAndAddModules().build();
    }
}

package lt.satsyuk.distributed.audit.contracts;

import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import lt.satsyuk.distributed.audit.contracts.command.DataDeletedCommand;
import lt.satsyuk.distributed.audit.contracts.command.EntityCreatedCommand;
import lt.satsyuk.distributed.audit.contracts.command.EntityUpdatedCommand;
import lt.satsyuk.distributed.audit.contracts.command.UserLoginCommand;
import lt.satsyuk.distributed.audit.contracts.command.UserProfileChangeCommand;
import lt.satsyuk.distributed.audit.contracts.config.CanonicalObjectMapperFactory;
import lt.satsyuk.distributed.audit.contracts.dto.AuditEventDto;
import lt.satsyuk.distributed.audit.contracts.dto.CommandResponse;
import lt.satsyuk.distributed.audit.event.EventType;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ContractsDtoAndMapperFactoryTest {

    @Test
    void commandBuildersPopulateAllFields() {
        UserLoginCommand loginCommand = UserLoginCommand.builder()
                .userId("user-1")
                .ipAddress("127.0.0.1")
                .userAgent("ua")
                .build();

        UserProfileChangeCommand profileChangeCommand = UserProfileChangeCommand.builder()
                .userId("user-2")
                .changedFields(Map.of("email", "new@example.com"))
                .build();

        EntityCreatedCommand createdCommand = EntityCreatedCommand.builder()
                .userId("user-3")
                .entityType("invoice")
                .entityId("inv-1")
                .entityData(Map.of("amount", 5))
                .build();

        EntityUpdatedCommand updatedCommand = EntityUpdatedCommand.builder()
                .userId("user-4")
                .entityType("invoice")
                .entityId("inv-2")
                .changedFields(Map.of("status", "PAID"))
                .build();

        DataDeletedCommand deletedCommand = DataDeletedCommand.builder()
                .userId("user-5")
                .entityType("invoice")
                .entityId("inv-3")
                .reason("retention")
                .build();

        assertThat(loginCommand.getUserId()).isEqualTo("user-1");
        assertThat(profileChangeCommand.getChangedFields()).containsEntry("email", "new@example.com");
        assertThat(createdCommand.getEntityData()).containsEntry("amount", 5);
        assertThat(updatedCommand.getChangedFields()).containsEntry("status", "PAID");
        assertThat(deletedCommand.getReason()).isEqualTo("retention");
    }

    @Test
    void commandResponseFactoriesProduceExpectedPayloads() {
        CommandResponse accepted = CommandResponse.accepted("evt-1");
        CommandResponse rejected = CommandResponse.rejected("validation failed");

        assertThat(accepted.isSuccess()).isTrue();
        assertThat(accepted.getMessage()).isEqualTo("Command accepted");
        assertThat(accepted.getEventId()).isEqualTo("evt-1");

        assertThat(rejected.isSuccess()).isFalse();
        assertThat(rejected.getMessage()).isEqualTo("validation failed");
        assertThat(rejected.getEventId()).isNull();
    }

    @Test
    void auditEventDtoBuilderStoresAllFields() {
        Instant occurredAt = Instant.parse("2026-05-22T10:15:30Z");
        AuditEventDto dto = AuditEventDto.builder()
                .id(1L)
                .eventId("evt-1")
                .eventType(EventType.USER_LOGGED_IN)
                .userId("user-1")
                .occurredAt(occurredAt)
                .eventDataJson("{\"k\":1}")
                .eventHash("abc123")
                .integrityStatus("ON_CHAIN")
                .build();

        assertThat(dto.getId()).isEqualTo(1L);
        assertThat(dto.getEventType()).isEqualTo(EventType.USER_LOGGED_IN);
        assertThat(dto.getOccurredAt()).isEqualTo(occurredAt);
        assertThat(dto.getIntegrityStatus()).isEqualTo("ON_CHAIN");
    }

    @Test
    void canonicalObjectMapperFactoryEnablesDeterministicOrderingAndIsoDates() throws Exception {
        ObjectMapper mapper = CanonicalObjectMapperFactory.create();

        assertThat(mapper.isEnabled(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY)).isTrue();
        assertThat(mapper.isEnabled(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS)).isTrue();
        assertThat(mapper.isEnabled(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)).isFalse();

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("b", 2);
        payload.put("a", Instant.parse("2026-05-22T00:00:00Z"));

        String json = mapper.writeValueAsString(payload);

        assertThat(json).isEqualTo("{\"a\":\"2026-05-22T00:00:00Z\",\"b\":2}");
    }
}


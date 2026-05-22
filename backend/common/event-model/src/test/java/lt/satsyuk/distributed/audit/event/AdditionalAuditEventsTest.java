package lt.satsyuk.distributed.audit.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class AdditionalAuditEventsTest {

    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    @Test
    void userProfileChangedFactoryPopulatesFields() {
        UserProfileChangedEvent event = UserProfileChangedEvent.of("user-10", Map.of("email", "new@example.com"));

        assertThat(event.getEventId()).isNotBlank();
        assertThat(event.getEventType()).isEqualTo(EventType.USER_PROFILE_CHANGED);
        assertThat(event.getOccurredAt()).isNotNull();
        assertThat(event.getSourceService()).isEqualTo("command-service");
        assertThat(event.getUserId()).isEqualTo("user-10");
        assertThat(event.getChangedFields()).containsEntry("email", "new@example.com");
    }

    @Test
    void entityCreatedFactoryPopulatesFields() {
        EntityCreatedEvent event = EntityCreatedEvent.of(
                "user-11",
                "invoice",
                "inv-1",
                Map.of("amount", 200)
        );

        assertThat(event.getEventType()).isEqualTo(EventType.ENTITY_CREATED);
        assertThat(event.getEntityType()).isEqualTo("invoice");
        assertThat(event.getEntityId()).isEqualTo("inv-1");
        assertThat(event.getEntityData()).containsEntry("amount", 200);
    }

    @Test
    void entityUpdatedFactoryPopulatesFields() {
        EntityUpdatedEvent event = EntityUpdatedEvent.of(
                "user-12",
                "invoice",
                "inv-2",
                Map.of("status", "PAID")
        );

        assertThat(event.getEventType()).isEqualTo(EventType.ENTITY_UPDATED);
        assertThat(event.getEntityType()).isEqualTo("invoice");
        assertThat(event.getEntityId()).isEqualTo("inv-2");
        assertThat(event.getChangedFields()).containsEntry("status", "PAID");
    }

    @Test
    void dataDeletedFactoryPopulatesFields() {
        DataDeletedEvent event = DataDeletedEvent.of("user-13", "invoice", "inv-3", "retention");

        assertThat(event.getEventType()).isEqualTo(EventType.DATA_DELETED);
        assertThat(event.getEntityType()).isEqualTo("invoice");
        assertThat(event.getEntityId()).isEqualTo("inv-3");
        assertThat(event.getReason()).isEqualTo("retention");
    }

    @Test
    void serializationRoundTripRestoresConcreteSubtypeForEntityCreated() throws Exception {
        EntityCreatedEvent original = EntityCreatedEvent.of("user-1", "contract", "c-1", Map.of("name", "NDA"));

        String json = objectMapper.writeValueAsString(original);
        AuditEvent restored = objectMapper.readValue(json, AuditEvent.class);

        assertThat(restored).isInstanceOf(EntityCreatedEvent.class);
        assertThat(restored.getEventType()).isEqualTo(EventType.ENTITY_CREATED);
        assertThat(((EntityCreatedEvent) restored).getEntityType()).isEqualTo("contract");
    }
}


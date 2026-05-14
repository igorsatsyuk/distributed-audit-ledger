package lt.satsyuk.distributed.audit.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class UserLoggedInEventTest {

	private final ObjectMapper objectMapper = new ObjectMapper()
			.registerModule(new JavaTimeModule());

	@Test
	void factoryMethod_populatesRequiredFields() {
		UserLoggedInEvent event = UserLoggedInEvent.of("user-42", "127.0.0.1", "Mozilla/5.0");

		assertThat(event.getEventId()).isNotBlank();
		assertThat(event.getEventType()).isEqualTo(EventType.USER_LOGGED_IN);
		assertThat(event.getOccurredAt()).isNotNull();
		assertThat(event.getSourceService()).isEqualTo("command-service");
		assertThat(event.getUserId()).isEqualTo("user-42");
		assertThat(event.getIpAddress()).isEqualTo("127.0.0.1");
		assertThat(event.getUserAgent()).isEqualTo("Mozilla/5.0");
	}

	@Test
	void serializationRoundTrip_preservesEventTypeAndPayload() throws Exception {
		UserLoggedInEvent original = UserLoggedInEvent.of("user-1", null, null);

		String json = objectMapper.writeValueAsString(original);
		AuditEvent restored = objectMapper.readValue(json, AuditEvent.class);

		assertThat(restored).isInstanceOf(UserLoggedInEvent.class);
		assertThat(restored.getEventType()).isEqualTo(EventType.USER_LOGGED_IN);
		assertThat(((UserLoggedInEvent) restored).getUserId()).isEqualTo("user-1");
	}
}


